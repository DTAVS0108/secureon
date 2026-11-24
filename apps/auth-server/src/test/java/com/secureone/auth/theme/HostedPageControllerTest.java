package com.secureone.auth.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.secureone.auth.application.ApplicationOAuthClientResolver;
import com.secureone.auth.application.ApplicationSettingsService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HostedPageControllerTest {

    @TempDir
    Path root;

    MockMvc mvc;
    ThemePages pages;

    static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setUp() throws IOException {
        write(root.resolve("acme/theme.css"), "body { color: rebeccapurple; }");
        write(root.resolve("acme/messages/en.json"), "{\"Sign in\": \"Log in to </script> Acme\"}");
        write(root.resolve("acme/assets/favicon.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");
        write(root.resolve("acme/assets/page.html"), "<p>not served</p>");
        write(root.resolve("acme/pages/account/forgot-password.html"),
                "<!DOCTYPE html><html><head><title>Acme reset</title><script src=\"/account/hosted.js\"></script></head>"
                        + "<body><h1>Acme forgot</h1></body></html>");
        write(root.resolve("acme/templates/consent.html"), "<html><head></head><body><h2>{{appName}}</h2>{{{content}}}</body></html>");
        ThemeRegistry themes = new ThemeRegistry(root.toString(), "base", false, null);
        var beans = new StaticListableBeanFactory();
        ThemeSelector selector = new ThemeSelector(themes, beans.getBeanProvider(ApplicationOAuthClientResolver.class),
                beans.getBeanProvider(ApplicationSettingsService.class));
        pages = new ThemePages(themes, selector);
        mvc = MockMvcBuilders.standaloneSetup(new HostedPageController(themes, pages)).build();
    }

    @Test
    void builtInPagesAreServedWithTheThemeMarker() throws Exception {
        String html = mvc.perform(get("/login.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("<meta name=\"secureone-theme\" content=\"base\" />").contains("id=\"login-form\"");
        assertThat(html).doesNotContain("/themes/").doesNotContain("secureone-theme-styles");
    }

    @Test
    void aThemeAddsItsStylesTextsAndIcon() throws Exception {
        String html = mvc.perform(get("/login.html").param("theme", "acme"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        int texts = html.indexOf("<script src=\"/themes/acme/messages.js\"></script>");
        int i18n = html.indexOf("<script src=\"/account/i18n.js\"></script>");
        int styles = html.indexOf("<link rel=\"stylesheet\" href=\"/themes/acme/theme.css\" />");
        assertThat(texts).isPositive().isLessThan(i18n);
        assertThat(styles).isGreaterThan(html.indexOf("</style>")).isLessThan(html.toLowerCase().indexOf("</head>"));
        assertThat(html).contains("href=\"/themes/acme/assets/favicon.svg\"").contains("id=\"login-form\"");
        // The theme styles the page: hosted.js leaves the application's colours to it (as CSS variables).
        assertThat(html.indexOf("<meta name=\"secureone-theme-styles\" content=\"on\" />")).isPositive().isLessThan(i18n);
    }

    @Test
    void aThemePageReplacesTheBuiltInOne() throws Exception {
        String html = mvc.perform(get("/account/forgot-password.html").param("theme", "acme"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Acme forgot").contains("/themes/acme/theme.css");
        String other = mvc.perform(get("/account/signup.html").param("theme", "acme"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(other).contains("Create account").contains("/themes/acme/theme.css");
    }

    @Test
    void unknownThemesAndPagesFallBackOrAreMissing() throws Exception {
        String html = mvc.perform(get("/login.html").param("theme", "nope"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("content=\"base\"");
        mvc.perform(get("/account/no-such-page.html")).andExpect(status().isNotFound());
    }

    @Test
    void themeFilesAreServedSafely() throws Exception {
        mvc.perform(get("/themes/acme/theme.css"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/css;charset=UTF-8"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string("body { color: rebeccapurple; }"));
        mvc.perform(get("/themes/acme/assets/favicon.svg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/svg+xml"))
                .andExpect(header().string("Content-Security-Policy", HostedPageController.ASSET_POLICY));
        mvc.perform(get("/themes/acme/assets/page.html")).andExpect(status().isNotFound());
        mvc.perform(get("/themes/acme/assets/..%2Ftheme.css")).andExpect(status().isNotFound());
        mvc.perform(get("/themes/nope/theme.css")).andExpect(status().isNotFound());
        mvc.perform(get("/themes/base/theme.css")).andExpect(status().isNotFound());
        mvc.perform(get("/themes/Bad/theme.css")).andExpect(status().isNotFound());
    }

    @Test
    void unchangedThemeFilesAnswerNotModified() throws Exception {
        String modified = mvc.perform(get("/themes/acme/theme.css")).andReturn().getResponse().getHeader("Last-Modified");
        assertThat(modified).isNotBlank();
        mvc.perform(get("/themes/acme/theme.css").header("If-Modified-Since", modified)).andExpect(status().isNotModified());
    }

    @Test
    void textsAreAScriptThatCannotBreakOut() throws Exception {
        String js = mvc.perform(get("/themes/acme/messages.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(js).startsWith("window.SecureOneTheme={\"name\":\"acme\"").contains("Log in to \\u003c/script\\u003e Acme");
        assertThat(js).doesNotContain("</script>");
        mvc.perform(get("/themes/nope/messages.js")).andExpect(status().isNotFound());
    }

    @Test
    void serverPagesUseTheThemeTemplateAndEscapeValues() {
        String consent = pages.render("acme", new ThemePages.Page("consent", "Allow x?", "Allow x?",
                "<form id=\"consent\"></form>", true, Map.of("appName", "<b>Shop</b>")));
        assertThat(consent).contains("<h2>&lt;b&gt;Shop&lt;/b&gt;</h2>").contains("<form id=\"consent\"></form>")
                .contains("/themes/acme/theme.css");
        String error = pages.render("acme", new ThemePages.Page("oauth-error", "Sign-in cannot start", "Cannot <start>",
                "<p>why</p>", true));
        assertThat(error).contains("<h1>Cannot &lt;start&gt;</h1>").contains("<p>why</p>")
                .contains("/account/hosted.js").contains("/themes/acme/messages.js");
        String post = pages.render("acme", new ThemePages.Page("saml-post", "Signing in", "Signing in…", "<form></form>", false));
        assertThat(post).doesNotContain("hosted.js").doesNotContain("messages.js").contains("/themes/acme/theme.css");
    }

    @Test
    void placeholdersTheTemplateDoesNotKnowStayAsWritten() {
        assertThat(ThemePages.fill("{{a}} {{{b}}} {{c}} {{{a}}}", Map.of("a", "<x>"), Map.of("b", "<y>")))
                .isEqualTo("&lt;x&gt; <y> {{c}} &lt;x&gt;");
    }
}
