package com.secureone.auth.theme;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * The hosted pages (sign-in, sign-up, password reset, account security…) in the request's theme, and the themes'
 * own files: /themes/&lt;name&gt;/theme.css, /themes/&lt;name&gt;/messages.js and /themes/&lt;name&gt;/assets/…. A page a
 * theme does not replace comes from its parent and in the end from the built-in pages. The pages' scripts
 * (/account/*.js) are SecureOne's and are not replaced by themes; a theme adds its own under assets/.
 */
@Hidden
@Controller
public class HostedPageController {

    /** What theme assets may be: no HTML (a theme cannot add pages under another URL than its own pages). */
    static final Map<String, MediaType> ASSET_TYPES = Map.ofEntries(
            Map.entry("css", MediaType.valueOf("text/css")),
            Map.entry("js", MediaType.valueOf("text/javascript")),
            Map.entry("mjs", MediaType.valueOf("text/javascript")),
            Map.entry("json", MediaType.APPLICATION_JSON),
            Map.entry("png", MediaType.IMAGE_PNG),
            Map.entry("jpg", MediaType.IMAGE_JPEG),
            Map.entry("jpeg", MediaType.IMAGE_JPEG),
            Map.entry("gif", MediaType.IMAGE_GIF),
            Map.entry("webp", MediaType.valueOf("image/webp")),
            Map.entry("avif", MediaType.valueOf("image/avif")),
            Map.entry("svg", MediaType.valueOf("image/svg+xml")),
            Map.entry("ico", MediaType.valueOf("image/x-icon")),
            Map.entry("woff", MediaType.valueOf("font/woff")),
            Map.entry("woff2", MediaType.valueOf("font/woff2")),
            Map.entry("ttf", MediaType.valueOf("font/ttf")),
            Map.entry("otf", MediaType.valueOf("font/otf")),
            Map.entry("txt", MediaType.TEXT_PLAIN));

    /** Opened on their own (an SVG, say), theme files cannot run scripts or load anything. */
    static final String ASSET_POLICY = "default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; sandbox";

    private final ThemeRegistry themes;
    private final ThemePages pages;

    public HostedPageController(ThemeRegistry themes, ThemePages pages) {
        this.themes = themes;
        this.pages = pages;
    }

    @GetMapping("/login.html")
    public ResponseEntity<String> login(HttpServletRequest request) {
        return page("login.html", request);
    }

    @GetMapping("/account/{page:[a-z0-9][a-z0-9-]{0,60}}.html")
    public ResponseEntity<String> account(@PathVariable String page, HttpServletRequest request) {
        return page("account/" + page + ".html", request);
    }

    private ResponseEntity<String> page(String path, HttpServletRequest request) {
        String theme = pages.select(request);
        Optional<ThemeRegistry.ThemeFile> file = themes.page(theme, path);
        if (file.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String html = pages.apply(new String(file.get().bytes(), StandardCharsets.UTF_8), theme);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                // The same URL shows another theme when the application's choice changes: never reuse a copy unchecked.
                .cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff")
                .body(html);
    }

    @GetMapping("/themes/{theme}/theme.css")
    public ResponseEntity<byte[]> stylesheet(@PathVariable String theme, HttpServletRequest request) {
        if (!ThemePages.validName(theme)) {
            return ResponseEntity.notFound().build();
        }
        return file(themes.stylesheet(theme), ASSET_TYPES.get("css"), request);
    }

    @GetMapping(value = "/themes/{theme}/messages.js")
    public ResponseEntity<String> messages(@PathVariable String theme) {
        if (!ThemePages.validName(theme) || !themes.exists(theme)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "javascript", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff")
                .body(pages.messagesScript(theme));
    }

    @GetMapping("/themes/{theme}/assets/**")
    public ResponseEntity<byte[]> asset(@PathVariable String theme, HttpServletRequest request) {
        String prefix = "/themes/" + theme + "/assets/";
        String uri = request.getRequestURI();
        if (!ThemePages.validName(theme) || !themes.exists(theme) || !uri.startsWith(prefix)) {
            return ResponseEntity.notFound().build();
        }
        String path = java.net.URLDecoder.decode(uri.substring(prefix.length()), StandardCharsets.UTF_8);
        int dot = path.lastIndexOf('.');
        MediaType type = dot < 0 ? null : ASSET_TYPES.get(path.substring(dot + 1).toLowerCase(Locale.ROOT));
        if (type == null || !ThemeRegistry.REL_PATH.matcher(path).matches()) {
            return ResponseEntity.notFound().build();
        }
        return file(themes.asset(theme, path), type, request);
    }

    private static ResponseEntity<byte[]> file(Optional<ThemeRegistry.ThemeFile> file, MediaType type, HttpServletRequest request) {
        if (file.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        long modified = file.get().lastModified();
        if (modified > 0 && new ServletWebRequest(request).checkNotModified(modified)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).cacheControl(CacheControl.noCache()).build();
        }
        MediaType contentType = type.getType().equals("text") || type.equals(MediaType.APPLICATION_JSON)
                ? new MediaType(type, StandardCharsets.UTF_8)
                : type;
        ResponseEntity.BodyBuilder ok = ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", ASSET_POLICY);
        if (modified > 0) {
            ok.lastModified(modified);
        }
        return ok.body(file.get().bytes());
    }
}
