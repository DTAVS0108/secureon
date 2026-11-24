package com.secureone.auth.theme;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Puts a theme on a page: the theme's stylesheets after the page's own styles, its texts and languages before the
 * page's scripts (/themes/&lt;name&gt;/messages.js, read by hosted.js), and its icon. Also renders the pages SecureOne
 * builds on the server from the theme's templates ({{value}} escaped, {{{value}}} as is).
 */
@Component
public class ThemePages {

    /** The scripts the server-built pages share with the hosted pages (translation, branding, language picker). */
    public static final String HOSTED_SCRIPTS =
            "<script src=\"/account/i18n.js\"></script><script src=\"/account/hosted.js\"></script>";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\{([a-zA-Z][a-zA-Z0-9]*)}}}|\\{\\{([a-zA-Z][a-zA-Z0-9]*)}}");
    private static final Pattern HEAD_END = Pattern.compile("</head\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIRST_SCRIPT = Pattern.compile("<script\\b", Pattern.CASE_INSENSITIVE);
    private static final String FAVICON = "href=\"/favicon.svg\"";

    private final ThemeRegistry themes;
    private final ThemeSelector selector;

    public ThemePages(ThemeRegistry themes, ThemeSelector selector) {
        this.themes = themes;
        this.selector = selector;
    }

    public String select(HttpServletRequest request) {
        return selector.select(request);
    }

    /** A page in its theme: see the class comment. Pages in {@code base} only get the theme name. */
    public String apply(String html, String theme) {
        return apply(html, theme, true);
    }

    /** {@code texts} false: no theme texts script (pages whose policy allows no scripts but their own). */
    String apply(String html, String theme, boolean texts) {
        String marker = "<meta name=\"secureone-theme\" content=\"" + HtmlUtils.htmlEscape(theme) + "\" />";
        StringBuilder early = new StringBuilder(marker);
        List<String> sheets = themes.stylesheets(theme);
        if (!sheets.isEmpty()) {
            // The theme styles the page: hosted.js then only provides the application's colours as CSS variables.
            early.append("<meta name=\"secureone-theme-styles\" content=\"on\" />");
        }
        if (texts && (!themes.messages(theme).isEmpty() || !themes.languages(theme).isEmpty())) {
            early.append("<script src=\"/themes/").append(theme).append("/messages.js\"></script>");
        }
        StringBuilder late = new StringBuilder();
        for (String owner : sheets) {
            late.append("<link rel=\"stylesheet\" href=\"/themes/").append(owner).append("/theme.css\" />");
        }
        String out = html;
        // Texts before the first script, so hosted.js knows the theme's languages when it starts.
        Matcher script = FIRST_SCRIPT.matcher(out);
        Matcher head = HEAD_END.matcher(out);
        int headEnd = head.find() ? head.start() : -1;
        if (script.find() && (headEnd < 0 || script.start() < headEnd)) {
            out = out.substring(0, script.start()) + early + out.substring(script.start());
        } else {
            late.insert(0, early);
        }
        head = HEAD_END.matcher(out);
        if (head.find()) {
            out = out.substring(0, head.start()) + late + out.substring(head.start());
        } else {
            out = late + out;
        }
        String icon = themes.assetOwner(theme, "favicon.svg").orElse(null);
        if (icon != null) {
            out = out.replace(FAVICON, "href=\"/themes/" + icon + "/assets/favicon.svg\"");
        }
        return out;
    }

    /** One server-built page; {@code values} need not be escaped (raw values are passed in {@code raw}). */
    public record Page(String name, String title, String heading, String content, boolean scripts,
            Map<String, String> values) {

        public Page(String name, String title, String heading, String content, boolean scripts) {
            this(name, title, heading, content, scripts, Map.of());
        }
    }

    /** The page in the request's theme (see {@link ThemeSelector}). */
    public String render(HttpServletRequest request, Page page) {
        return render(request == null ? themes.defaultTheme() : select(request), page);
    }

    /** The page in the server's default theme (pages that do not know their application). */
    public String renderDefault(Page page) {
        return render(themes.defaultTheme(), page);
    }

    public String render(String theme, Page page) {
        if (!ThemeRegistry.SERVER_PAGES.contains(page.name())) {
            throw new IllegalArgumentException("Unknown server page: " + page.name());
        }
        String template = themes.template(theme, page.name())
                .map(f -> new String(f.bytes(), StandardCharsets.UTF_8))
                .orElseGet(() -> themes.template(ThemeRegistry.BASE, page.name())
                        .map(f -> new String(f.bytes(), StandardCharsets.UTF_8))
                        .orElseThrow());
        Map<String, String> escaped = new LinkedHashMap<>(page.values());
        escaped.put("lang", "en");
        escaped.put("page", page.name());
        escaped.put("title", page.title());
        escaped.put("heading", page.heading());
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("content", page.content() == null ? "" : page.content());
        raw.put("head", page.scripts() ? HOSTED_SCRIPTS : "");
        return apply(fill(template, escaped, raw), theme, page.scripts());
    }

    /** Replaces the placeholders it knows; unknown ones stay as written (a comment may mention them). */
    static String fill(String template, Map<String, String> escaped, Map<String, String> raw) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length() + 256);
        while (m.find()) {
            String value;
            if (m.group(1) != null) {
                value = raw.containsKey(m.group(1)) ? raw.get(m.group(1))
                        : escaped.containsKey(m.group(1)) ? HtmlUtils.htmlEscape(nullToEmpty(escaped.get(m.group(1)))) : null;
            } else {
                value = escaped.containsKey(m.group(2)) ? HtmlUtils.htmlEscape(nullToEmpty(escaped.get(m.group(2))))
                        : null;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? m.group() : value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** {@code window.SecureOneTheme}: the theme's name, texts and extra languages (see hosted.js). */
    public String messagesScript(String theme) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", theme);
        data.put("messages", themes.messages(theme));
        data.put("languages", themes.languages(theme));
        return "window.SecureOneTheme=" + JsonText.of(data) + ";\n";
    }

    /** Whether a theme name is well formed (lowercase letters, digits, '-' and '_'). */
    public static boolean validName(String name) {
        return name != null && ThemeRegistry.NAME.matcher(name).matches();
    }

    /** Minimal JSON for maps, lists and strings, with "<" escaped so it is safe anywhere in a page. */
    static final class JsonText {
        private JsonText() {}

        static String of(Object value) {
            StringBuilder out = new StringBuilder();
            write(out, value);
            return out.toString();
        }

        private static void write(StringBuilder out, Object value) {
            if (value == null) {
                out.append("null");
            } else if (value instanceof Map<?, ?> map) {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    string(out, String.valueOf(e.getKey()));
                    out.append(':');
                    write(out, e.getValue());
                }
                out.append('}');
            } else if (value instanceof List<?> list) {
                out.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    write(out, list.get(i));
                }
                out.append(']');
            } else if (value instanceof Boolean || value instanceof Number) {
                out.append(value);
            } else {
                string(out, String.valueOf(value));
            }
        }

        private static void string(StringBuilder out, String s) {
            out.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    case '<' -> out.append("\\u003c");
                    case '>' -> out.append("\\u003e");
                    case '&' -> out.append("\\u0026");
                    case ' ' -> out.append("\\u2028");
                    case ' ' -> out.append("\\u2029");
                    default -> {
                        if (c < 0x20) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
            out.append('"');
        }
    }
}
