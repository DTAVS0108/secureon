package com.secureone.auth.theme;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The sign-in page themes: the built-in {@code base} and the folders under {@code SECUREONE_THEMES_DIR}, one per
 * theme. A theme holds only what it changes; anything else comes from its parent (theme.json {@code parent},
 * {@code base} by default), so a theme can be one stylesheet or a full set of pages. Layout of a theme folder:
 *
 * <pre>
 *   theme.json            { "parent": "base", "secureone": "0.1.1", "description": "…", "languages": [...] }
 *   theme.css             added to every hosted page (after the page's own styles)
 *   messages/&lt;lang&gt;.json  { "English text on the page": "your text" }
 *   assets/…              images, fonts, scripts: /themes/&lt;name&gt;/assets/…
 *   pages/login.html, pages/account/&lt;page&gt;.html   replace a hosted page
 *   templates/page.html, templates/&lt;page&gt;.html    replace the layout of the pages built on the server
 * </pre>
 *
 * Files are only read inside the themes folder (no "..", no links pointing out of it). With the cache on
 * (production) a theme is read once and {@link #reload()} picks up changes; otherwise the folder is read again at
 * most once a second, so edits to a mounted theme show on the next page load.
 */
@Component
public class ThemeRegistry {

    private static final Logger log = LoggerFactory.getLogger(ThemeRegistry.class);

    public static final String BASE = "base";
    static final Pattern NAME = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,39}$");
    /** Relative file paths inside a theme: plain names joined by "/", nothing hidden, no "..". */
    static final Pattern REL_PATH = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9._-]*(/[A-Za-z0-9_][A-Za-z0-9._-]*)*$");
    static final Pattern LANG_FILE = Pattern.compile("^([a-z]{2,3})(?:[-_][A-Za-z0-9]{2,8})?\\.json$");
    static final Pattern LANG_CODE = Pattern.compile("^[a-z]{2,3}$");
    private static final int MAX_PARENTS = 5;
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final long RESCAN_MILLIS = 1000;
    private static final String BASE_RESOURCES = "secureone-theme-base/";

    /** One installed theme. {@code dir} is null for the built-in theme. */
    public record Theme(
            String name,
            String parent,
            String description,
            String madeFor,
            Path dir,
            List<Map<String, String>> languages,
            List<String> problems) {

        public boolean builtIn() {
            return dir == null;
        }
    }

    /** A theme file: its bytes, when it last changed, and which theme of the chain it came from. */
    public record ThemeFile(byte[] bytes, long lastModified, String theme, String path) {}

    private record Snapshot(Map<String, Theme> themes, List<String> problems, Path realRoot, long readAt) {}

    private final ObjectMapper json = new ObjectMapper();
    private final Path root;
    private final String configuredDefault;
    private final boolean cache;
    private final String serverVersion;
    private final Map<String, String> pageVersions;
    private final Map<String, Optional<ThemeFile>> fileCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<ThemeFile>> baseFiles = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, String>>> messageCache = new ConcurrentHashMap<>();
    private volatile Snapshot snapshot;

    public ThemeRegistry(
            @Value("${secureone.themes.dir:}") String dir,
            @Value("${secureone.themes.default:base}") String defaultTheme,
            @Value("${secureone.themes.cache:false}") boolean cache,
            ObjectProvider<BuildProperties> build) {
        this.root = dir == null || dir.isBlank() ? null : Paths.get(dir.trim()).toAbsolutePath().normalize();
        this.configuredDefault = defaultTheme == null || defaultTheme.isBlank() ? BASE : defaultTheme.trim();
        this.cache = cache;
        BuildProperties properties = build == null ? null : build.getIfAvailable();
        this.serverVersion = properties != null && properties.getVersion() != null ? properties.getVersion() : null;
        this.pageVersions = readPageVersions();
    }

    // ---- What is installed -----------------------------------------------------------------------------------

    public boolean exists(String name) {
        return name != null && snapshot().themes().containsKey(name);
    }

    /** The themes, the built-in one first. */
    public List<Theme> themes() {
        return List.copyOf(snapshot().themes().values());
    }

    public Optional<Theme> theme(String name) {
        return Optional.ofNullable(name == null ? null : snapshot().themes().get(name));
    }

    /** SECUREONE_THEME when that theme is installed, otherwise {@code base}. */
    public String defaultTheme() {
        return exists(configuredDefault) ? configuredDefault : BASE;
    }

    public String configuredDefault() {
        return configuredDefault;
    }

    public String themesDir() {
        return root == null ? null : root.toString();
    }

    public boolean cacheEnabled() {
        return cache;
    }

    /** Problems found while reading the themes folder (not tied to one theme). */
    public List<String> problems() {
        List<String> out = new ArrayList<>(snapshot().problems());
        if (!exists(configuredDefault)) {
            out.add("SECUREONE_THEME is '" + configuredDefault + "', but no such theme is installed: the built-in theme is used.");
        }
        return out;
    }

    /** The theme and its parents, the theme first and {@code base} last. An unknown name gives {@code [base]}. */
    public List<Theme> chain(String name) {
        Map<String, Theme> themes = snapshot().themes();
        List<Theme> out = new ArrayList<>();
        Theme current = themes.get(name == null ? BASE : name);
        if (current == null) {
            current = themes.get(BASE);
        }
        Set<String> seen = new HashSet<>();
        while (current != null && seen.add(current.name())) {
            out.add(current);
            current = current.parent() == null ? null : themes.get(current.parent());
        }
        if (out.isEmpty() || !out.get(out.size() - 1).builtIn()) {
            out.add(themes.get(BASE));
        }
        return out;
    }

    /** Drops what was read, so the next page reads the themes folder again (the console's Reload). */
    public void reload() {
        fileCache.clear();
        messageCache.clear();
        snapshot = null;
        Snapshot fresh = snapshot();
        log.info("Sign-in themes reloaded: {}", summary(fresh));
    }

    // ---- Files -------------------------------------------------------------------------------------------------

    /** A hosted page ("login.html", "account/signup.html") from the theme or the nearest parent that has it. */
    public Optional<ThemeFile> page(String theme, String page) {
        return inChain(theme, "pages/" + page);
    }

    /**
     * A server-built page's template: for each theme of the chain, its templates/&lt;page&gt;.html, then its
     * templates/page.html (the nearest theme wins, so a theme's own layout beats a parent's single-page template).
     */
    public Optional<ThemeFile> template(String theme, String page) {
        for (Theme t : chain(theme)) {
            Optional<ThemeFile> specific = file(t, "templates/" + page + ".html");
            if (specific.isPresent()) {
                return specific;
            }
            Optional<ThemeFile> layout = file(t, "templates/page.html");
            if (layout.isPresent()) {
                return layout;
            }
        }
        return Optional.empty();
    }

    /** A file under assets/ of the theme or a parent. */
    public Optional<ThemeFile> asset(String theme, String path) {
        return inChain(theme, "assets/" + path);
    }

    /** The theme's own theme.css (each theme of a chain adds its own; parents' come first on the page). */
    public Optional<ThemeFile> stylesheet(String theme) {
        return theme(theme).flatMap(t -> file(t, "theme.css"));
    }

    /** The themes of the chain that have a theme.css, the outermost parent first (the page's link order). */
    public List<String> stylesheets(String theme) {
        List<String> out = new ArrayList<>();
        List<Theme> chain = chain(theme);
        for (int i = chain.size() - 1; i >= 0; i--) {
            Theme t = chain.get(i);
            if (file(t, "theme.css").isPresent()) {
                out.add(t.name());
            }
        }
        return out;
    }

    /** The theme of the chain that has assets/&lt;name&gt; (for the page icon), the theme itself first. */
    public Optional<String> assetOwner(String theme, String name) {
        return asset(theme, name).map(ThemeFile::theme);
    }

    /**
     * The chain's texts per language ({ "en": { "Sign in": "Log in" } }), parents first so the theme's own win.
     * Language codes are the file names' first part (messages/pt-BR.json counts as "pt").
     */
    public Map<String, Map<String, String>> messages(String theme) {
        String key = chain(theme).stream().map(Theme::name).reduce((a, b) -> a + ">" + b).orElse(BASE);
        if (cache) {
            return messageCache.computeIfAbsent(key, k -> readMessages(theme));
        }
        return readMessages(theme);
    }

    /** Extra languages for the page's language picker (theme.json "languages"), parents first. */
    public List<Map<String, String>> languages(String theme) {
        Map<String, Map<String, String>> byCode = new LinkedHashMap<>();
        List<Theme> chain = chain(theme);
        for (int i = chain.size() - 1; i >= 0; i--) {
            for (Map<String, String> language : chain.get(i).languages()) {
                byCode.put(language.get("code"), language);
            }
        }
        return List.copyOf(byCode.values());
    }

    // ---- Console description -----------------------------------------------------------------------------------

    /** What each theme changes, and anything wrong with it (Platform settings → Sign-in themes). */
    public List<Map<String, Object>> describe() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Theme theme : themes()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", theme.name());
            row.put("builtIn", theme.builtIn());
            row.put("parent", theme.parent());
            row.put("description", theme.description());
            row.put("madeFor", theme.madeFor());
            row.put("stylesheet", !theme.builtIn() && file(theme, "theme.css").isPresent());
            List<String> pages = theme.builtIn() ? List.of() : listFiles(theme, "pages");
            List<String> templates = theme.builtIn() ? List.of() : listFiles(theme, "templates");
            row.put("pages", pages);
            row.put("templates", templates);
            row.put("messageLanguages", theme.builtIn() ? List.of() : new ArrayList<>(readOwnMessages(theme).keySet()));
            row.put("assets", theme.builtIn() ? 0 : listFiles(theme, "assets").size());
            List<String> problems = new ArrayList<>(theme.problems());
            List<String> outdated = new ArrayList<>();
            for (String page : pages) {
                checkVersion(theme, "pages/" + page, outdated, problems);
            }
            for (String template : templates) {
                checkVersion(theme, "templates/" + template, outdated, problems);
            }
            row.put("outdated", outdated);
            row.put("problems", problems.stream().distinct().toList());
            out.add(row);
        }
        return out;
    }

    private void checkVersion(Theme theme, String path, List<String> outdated, List<String> problems) {
        String changedIn = pageVersions.get(path);
        if (changedIn == null) {
            if (path.startsWith("pages/")) {
                // A page of the theme's own (an extra page): nothing to compare with.
                return;
            }
            if (!path.equals("templates/page.html") && !SERVER_PAGES.contains(templateName(path))) {
                problems.add(path + " is not used: server pages are " + String.join(", ", SERVER_PAGES) + ".");
            }
            return;
        }
        if (theme.madeFor() == null) {
            problems.add("theme.json has no \"secureone\" version, so SecureOne cannot tell whether the copied pages "
                    + "are up to date. Set it to the version the pages were copied from.");
            return;
        }
        if (compareVersions(changedIn, theme.madeFor()) > 0) {
            outdated.add(path + " (the built-in page changed in " + changedIn + ")");
        }
    }

    /** Pages SecureOne builds on the server (templates/&lt;name&gt;.html). */
    public static final List<String> SERVER_PAGES = List.of(
            "consent", "oauth-error", "email-verified", "email-verify-failed", "magic-link-invalid", "saml-error",
            "saml-post");

    private static String templateName(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.endsWith(".html") ? name.substring(0, name.length() - 5) : name;
    }

    /** Version compare on the numeric parts ("0.1.10" after "0.1.9"); anything else counts as 0. */
    static int compareVersions(String a, String b) {
        String[] x = a.split("[.\\-+]");
        String[] y = b.split("[.\\-+]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? number(x[i]) : 0;
            int q = i < y.length ? number(y[i]) : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        return 0;
    }

    private static int number(String part) {
        try {
            return Integer.parseInt(part.replaceAll("\\D.*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void logThemes() {
        Snapshot s = snapshot();
        log.info("Sign-in themes: {} (default {}, cache {})", summary(s), defaultTheme(), cache ? "on" : "off");
        problems().forEach(p -> log.warn("Sign-in themes: {}", p));
        for (Map<String, Object> row : describe()) {
            for (Object problem : (List<?>) row.get("problems")) {
                log.warn("Sign-in theme '{}': {}", row.get("name"), problem);
            }
            for (Object page : (List<?>) row.get("outdated")) {
                log.warn("Sign-in theme '{}' may be out of date: {}. Compare it with the built-in page.", row.get("name"), page);
            }
        }
        if (serverVersion != null) {
            log.debug("Sign-in themes checked against SecureOne {}", serverVersion);
        }
    }

    private static String summary(Snapshot s) {
        return s.themes().values().stream()
                .map(t -> t.builtIn() ? t.name() + " (built-in)" : t.name() + " (parent " + t.parent() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse(BASE);
    }

    // ---- Reading -------------------------------------------------------------------------------------------------

    private Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current != null && (cache || System.currentTimeMillis() - current.readAt() < RESCAN_MILLIS)) {
            return current;
        }
        synchronized (this) {
            current = snapshot;
            if (current != null && (cache || System.currentTimeMillis() - current.readAt() < RESCAN_MILLIS)) {
                return current;
            }
            Snapshot fresh = scan();
            if (!cache) {
                fileCache.clear();
            }
            snapshot = fresh;
            return fresh;
        }
    }

    private Snapshot scan() {
        Map<String, Theme> found = new LinkedHashMap<>();
        found.put(BASE, new Theme(BASE, null, "The built-in pages", serverVersion, null, List.of(), List.of()));
        List<String> problems = new ArrayList<>();
        Path realRoot = null;
        if (root != null) {
            if (!Files.isDirectory(root)) {
                problems.add("The themes folder " + root + " (SECUREONE_THEMES_DIR) does not exist: only the built-in theme is available.");
            } else {
                try {
                    realRoot = root.toRealPath();
                } catch (IOException e) {
                    problems.add("The themes folder " + root + " cannot be read: " + e.getMessage());
                }
            }
        }
        Map<String, Theme> raw = new TreeMap<>();
        if (realRoot != null) {
            List<Path> folders = new ArrayList<>();
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(realRoot)) {
                entries.forEach(folders::add);
            } catch (IOException e) {
                problems.add("The themes folder " + root + " cannot be read: " + e.getMessage());
            }
            folders.sort(Comparator.comparing(p -> p.getFileName().toString()));
            for (Path folder : folders) {
                String name = folder.getFileName().toString();
                if (name.startsWith(".") || !Files.isDirectory(folder)) {
                    continue;
                }
                if (BASE.equals(name)) {
                    problems.add("The folder 'base' is ignored: 'base' is the built-in theme. Give your copy another name "
                            + "(theme.json \"parent\": \"base\" keeps everything you do not change).");
                    continue;
                }
                if (!NAME.matcher(name).matches()) {
                    problems.add("The folder '" + name + "' is ignored: theme names are lowercase letters, digits, '-' "
                            + "and '_' (up to 40, starting with a letter or digit).");
                    continue;
                }
                raw.put(name, readTheme(name, folder));
            }
        }
        // Parents: unknown ones and loops fall back to base, with a note.
        for (Theme theme : raw.values()) {
            List<String> themeProblems = new ArrayList<>(theme.problems());
            String parent = theme.parent();
            if (!BASE.equals(parent) && !raw.containsKey(parent)) {
                themeProblems.add("Parent theme '" + parent + "' is not installed: 'base' is used instead.");
                parent = BASE;
            } else if (loops(theme.name(), raw)) {
                themeProblems.add("The parent chain loops back to this theme or is deeper than " + MAX_PARENTS
                        + " themes: 'base' is used as its parent.");
                parent = BASE;
            }
            found.put(theme.name(), new Theme(theme.name(), parent, theme.description(), theme.madeFor(), theme.dir(),
                    theme.languages(), List.copyOf(themeProblems)));
        }
        return new Snapshot(Collections.unmodifiableMap(found), List.copyOf(problems), realRoot, System.currentTimeMillis());
    }

    private static boolean loops(String start, Map<String, Theme> raw) {
        Set<String> seen = new HashSet<>();
        String current = start;
        int depth = 0;
        while (current != null && !BASE.equals(current)) {
            if (!seen.add(current) || ++depth > MAX_PARENTS) {
                return true;
            }
            Theme t = raw.get(current);
            current = t == null ? null : t.parent();
        }
        return false;
    }

    private Theme readTheme(String name, Path folder) {
        List<String> problems = new ArrayList<>();
        String parent = BASE;
        String description = null;
        String madeFor = null;
        List<Map<String, String>> languages = new ArrayList<>();
        Path manifest = folder.resolve("theme.json");
        if (Files.isRegularFile(manifest)) {
            try {
                JsonNode node = json.readTree(Files.readAllBytes(manifest));
                if (node == null || !node.isObject()) {
                    problems.add("theme.json is not a JSON object.");
                } else {
                    if (node.hasNonNull("parent")) {
                        String p = node.get("parent").asText().trim();
                        if (p.equals(name)) {
                            problems.add("theme.json names the theme as its own parent: 'base' is used.");
                        } else if (BASE.equals(p) || NAME.matcher(p).matches()) {
                            parent = p;
                        } else {
                            problems.add("theme.json \"parent\" '" + p + "' is not a theme name: 'base' is used.");
                        }
                    }
                    if (node.hasNonNull("description")) {
                        description = node.get("description").asText();
                    }
                    if (node.hasNonNull("secureone")) {
                        madeFor = node.get("secureone").asText().trim().replaceFirst("^[>=v ]+", "");
                    }
                    if (node.has("languages")) {
                        readLanguages(node.get("languages"), languages, problems);
                    }
                }
            } catch (IOException e) {
                problems.add("theme.json cannot be read: " + e.getMessage());
            }
        }
        return new Theme(name, parent, description, madeFor, folder, List.copyOf(languages), problems);
    }

    private static void readLanguages(JsonNode node, List<Map<String, String>> out, List<String> problems) {
        if (!node.isArray()) {
            problems.add("theme.json \"languages\" must be a list of { \"code\": \"ta\", \"name\": \"தமிழ்\" }.");
            return;
        }
        for (JsonNode item : node) {
            String code = item.path("code").asText("").trim().toLowerCase(java.util.Locale.ROOT);
            String label = item.path("name").asText("").trim();
            if (!LANG_CODE.matcher(code).matches() || label.isEmpty() || label.length() > 40) {
                problems.add("theme.json language " + item + " needs a 2–3 letter \"code\" and a \"name\" (up to 40 characters).");
                continue;
            }
            out.add(Map.of("code", code, "name", label));
        }
    }

    private Optional<ThemeFile> inChain(String theme, String path) {
        if (!REL_PATH.matcher(path).matches()) {
            return Optional.empty();
        }
        for (Theme t : chain(theme)) {
            Optional<ThemeFile> file = file(t, path);
            if (file.isPresent()) {
                return file;
            }
        }
        return Optional.empty();
    }

    /** One file of one theme (no parents). */
    Optional<ThemeFile> file(Theme theme, String path) {
        if (!REL_PATH.matcher(path).matches()) {
            return Optional.empty();
        }
        if (theme.builtIn()) {
            return baseFiles.computeIfAbsent(path, ThemeRegistry::readBase);
        }
        String key = theme.name() + "|" + path;
        Optional<ThemeFile> cached = fileCache.get(key);
        if (cached != null) {
            return cached;
        }
        Optional<ThemeFile> read = readDisk(theme, path);
        fileCache.put(key, read);
        return read;
    }

    private Optional<ThemeFile> readDisk(Theme theme, String path) {
        Snapshot s = snapshot();
        if (s.realRoot() == null) {
            return Optional.empty();
        }
        Path file = theme.dir().resolve(path).normalize();
        if (!file.startsWith(theme.dir()) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            // Links inside the themes folder are fine (Kubernetes ConfigMaps use them); out of it they are not.
            Path real = file.toRealPath();
            if (!real.startsWith(s.realRoot())) {
                log.warn("Sign-in theme '{}': {} points outside the themes folder and is ignored", theme.name(), path);
                return Optional.empty();
            }
            long size = Files.size(real);
            if (size > MAX_FILE_BYTES) {
                log.warn("Sign-in theme '{}': {} is larger than 5 MB and is ignored", theme.name(), path);
                return Optional.empty();
            }
            return Optional.of(new ThemeFile(Files.readAllBytes(real), Files.getLastModifiedTime(real).toMillis(),
                    theme.name(), path));
        } catch (IOException e) {
            log.warn("Sign-in theme '{}': {} cannot be read: {}", theme.name(), path, e.getMessage());
            return Optional.empty();
        }
    }

    /** Built-in files: the hosted pages are the application's static pages; templates live beside this class. */
    private static Optional<ThemeFile> readBase(String path) {
        String resource;
        if (path.startsWith("pages/") && path.endsWith(".html")) {
            resource = "static/" + path.substring("pages/".length());
        } else if (path.startsWith("templates/")) {
            resource = BASE_RESOURCES + path;
        } else {
            return Optional.empty();
        }
        ClassPathResource file = new ClassPathResource(resource);
        if (!file.exists()) {
            return Optional.empty();
        }
        try (InputStream in = file.getInputStream()) {
            long modified;
            try {
                modified = file.lastModified();
            } catch (IOException e) {
                modified = 0;
            }
            return Optional.of(new ThemeFile(in.readAllBytes(), modified, BASE, path));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Map<String, Map<String, String>> readMessages(String theme) {
        Map<String, Map<String, String>> merged = new TreeMap<>();
        List<Theme> chain = chain(theme);
        for (int i = chain.size() - 1; i >= 0; i--) {
            readOwnMessages(chain.get(i)).forEach((lang, texts) ->
                    merged.computeIfAbsent(lang, l -> new LinkedHashMap<>()).putAll(texts));
        }
        return merged;
    }

    private Map<String, Map<String, String>> readOwnMessages(Theme theme) {
        Map<String, Map<String, String>> out = new TreeMap<>();
        if (theme.builtIn()) {
            return out;
        }
        for (String name : listFiles(theme, "messages")) {
            var m = LANG_FILE.matcher(name);
            if (!m.matches()) {
                continue;
            }
            Optional<ThemeFile> file = file(theme, "messages/" + name);
            if (file.isEmpty()) {
                continue;
            }
            try {
                JsonNode node = json.readTree(file.get().bytes());
                if (node == null || !node.isObject()) {
                    log.warn("Sign-in theme '{}': messages/{} is not a JSON object and is ignored", theme.name(), name);
                    continue;
                }
                Map<String, String> texts = out.computeIfAbsent(m.group(1), l -> new LinkedHashMap<>());
                node.fields().forEachRemaining(e -> {
                    if (e.getValue().isTextual() && !e.getKey().isBlank()) {
                        texts.put(e.getKey().trim(), e.getValue().asText());
                    }
                });
            } catch (IOException e) {
                log.warn("Sign-in theme '{}': messages/{} is not valid JSON and is ignored ({})", theme.name(), name,
                        e.getMessage());
            }
        }
        return out;
    }

    /** Files under one folder of a theme, as paths relative to that folder ("account/signup.html"). */
    private List<String> listFiles(Theme theme, String folder) {
        Path dir = theme.dir().resolve(folder);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(dir, 4)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> dir.relativize(p).toString().replace('\\', '/'))
                    .filter(p -> REL_PATH.matcher(p).matches())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private Map<String, String> readPageVersions() {
        ClassPathResource file = new ClassPathResource(BASE_RESOURCES + "pages.json");
        Map<String, String> out = new LinkedHashMap<>();
        try (InputStream in = file.getInputStream()) {
            json.readTree(in).fields().forEachRemaining(e -> {
                if (!e.getKey().startsWith("_") && e.getValue().isTextual()) {
                    out.put(e.getKey(), e.getValue().asText());
                }
            });
        } catch (IOException e) {
            log.warn("Built-in page versions (pages.json) cannot be read: {}", e.getMessage());
        }
        return Map.copyOf(out);
    }
}
