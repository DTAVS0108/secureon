package com.secureone.auth.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeRegistryTest {

    @TempDir
    Path root;

    static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    ThemeRegistry registry(boolean cache) {
        return new ThemeRegistry(root.toString(), "base", cache, null);
    }

    static String text(ThemeRegistry.ThemeFile file) {
        return new String(file.bytes(), StandardCharsets.UTF_8);
    }

    @Test
    void withoutAFolderOnlyTheBuiltInThemeExists() {
        ThemeRegistry themes = new ThemeRegistry("", "acme", false, null);
        assertThat(themes.themes()).extracting(ThemeRegistry.Theme::name).containsExactly("base");
        assertThat(themes.defaultTheme()).isEqualTo("base");
        assertThat(themes.problems()).anyMatch(p -> p.contains("SECUREONE_THEME is 'acme'"));
        assertThat(themes.page("anything", "login.html")).isPresent();
        assertThat(text(themes.page("base", "account/forgot-password.html").orElseThrow())).contains("Forgot password");
        assertThat(themes.template("base", "consent")).isPresent();
    }

    @Test
    void aThemeReplacesOnlyWhatItHas() throws IOException {
        write(root.resolve("acme/theme.json"), "{\"description\":\"Acme\",\"secureone\":\"0.1.1\"}");
        write(root.resolve("acme/theme.css"), "body{color:red}");
        write(root.resolve("acme/pages/login.html"), "<html><head></head><body>Acme sign-in</body></html>");
        ThemeRegistry themes = registry(false);

        assertThat(themes.exists("acme")).isTrue();
        assertThat(themes.theme("acme").orElseThrow().parent()).isEqualTo("base");
        assertThat(text(themes.page("acme", "login.html").orElseThrow())).contains("Acme sign-in");
        ThemeRegistry.ThemeFile signup = themes.page("acme", "account/signup.html").orElseThrow();
        assertThat(signup.theme()).isEqualTo("base");
        assertThat(themes.stylesheets("acme")).containsExactly("acme");
        assertThat(themes.stylesheets("base")).isEmpty();
    }

    @Test
    void parentsAreInheritedAndTheirStylesComeFirst() throws IOException {
        write(root.resolve("corp/theme.css"), "/* corp */");
        write(root.resolve("corp/assets/logo.svg"), "<svg/>");
        write(root.resolve("corp/messages/en.json"), "{\"Sign in\":\"Corp sign-in\",\"Email\":\"Work email\"}");
        write(root.resolve("acme/theme.json"), "{\"parent\":\"corp\",\"languages\":[{\"code\":\"ta\",\"name\":\"தமிழ்\"}]}");
        write(root.resolve("acme/theme.css"), "/* acme */");
        write(root.resolve("acme/messages/en.json"), "{\"Sign in\":\"Acme sign-in\"}");
        write(root.resolve("acme/messages/pt-BR.json"), "{\"Sign in\":\"Entrar na Acme\"}");
        ThemeRegistry themes = registry(false);

        assertThat(themes.chain("acme")).extracting(ThemeRegistry.Theme::name).containsExactly("acme", "corp", "base");
        assertThat(themes.stylesheets("acme")).containsExactly("corp", "acme");
        assertThat(themes.asset("acme", "logo.svg")).map(ThemeRegistry.ThemeFile::theme).contains("corp");
        Map<String, Map<String, String>> messages = themes.messages("acme");
        assertThat(messages.get("en")).containsEntry("Sign in", "Acme sign-in").containsEntry("Email", "Work email");
        assertThat(messages.get("pt")).containsEntry("Sign in", "Entrar na Acme");
        assertThat(themes.languages("acme")).containsExactly(Map.of("code", "ta", "name", "தமிழ்"));
    }

    @Test
    void badFoldersAndParentsAreReportedAndFallBack() throws IOException {
        write(root.resolve("Bad Name/theme.css"), "");
        write(root.resolve("base/theme.css"), "");
        write(root.resolve(".hidden/theme.css"), "");
        write(root.resolve("orphan/theme.json"), "{\"parent\":\"missing\"}");
        write(root.resolve("loop-a/theme.json"), "{\"parent\":\"loop-b\"}");
        write(root.resolve("loop-b/theme.json"), "{\"parent\":\"loop-a\"}");
        write(root.resolve("broken/theme.json"), "{not json");
        ThemeRegistry themes = registry(false);

        assertThat(themes.themes()).extracting(ThemeRegistry.Theme::name)
                .containsExactly("base", "broken", "loop-a", "loop-b", "orphan");
        assertThat(themes.problems()).anyMatch(p -> p.contains("'Bad Name' is ignored")).anyMatch(p -> p.contains("'base' is ignored"));
        assertThat(themes.theme("orphan").orElseThrow().parent()).isEqualTo("base");
        assertThat(themes.theme("orphan").orElseThrow().problems()).anyMatch(p -> p.contains("'missing' is not installed"));
        assertThat(themes.theme("loop-a").orElseThrow().problems()).anyMatch(p -> p.contains("loops"));
        assertThat(themes.chain("loop-a")).last().extracting(ThemeRegistry.Theme::name).isEqualTo("base");
        assertThat(themes.theme("broken").orElseThrow().problems()).anyMatch(p -> p.contains("cannot be read"));
    }

    @Test
    void filesOutsideTheThemeAreNeverRead() throws IOException {
        write(root.resolve("acme/theme.css"), "");
        write(root.resolve("secret.txt"), "secret");
        ThemeRegistry themes = registry(false);
        assertThat(themes.asset("acme", "../secret.txt")).isEmpty();
        assertThat(themes.asset("acme", "../../etc/passwd")).isEmpty();
        assertThat(themes.page("acme", "../acme/theme.css")).isEmpty();
        assertThat(themes.asset("acme", ".hidden")).isEmpty();
        assertThat(ThemeRegistry.REL_PATH.matcher("a/../b").matches()).isFalse();
        assertThat(ThemeRegistry.REL_PATH.matcher("img/logo.svg").matches()).isTrue();
    }

    @Test
    void withoutTheCacheEditsShowWithTheCacheAfterReload() throws Exception {
        write(root.resolve("acme/theme.css"), "one");
        ThemeRegistry live = registry(false);
        ThemeRegistry cached = registry(true);
        assertThat(text(live.stylesheet("acme").orElseThrow())).isEqualTo("one");
        assertThat(text(cached.stylesheet("acme").orElseThrow())).isEqualTo("one");

        write(root.resolve("acme/theme.css"), "two");
        write(root.resolve("fresh/theme.css"), "");
        Thread.sleep(1100);
        assertThat(text(live.stylesheet("acme").orElseThrow())).isEqualTo("two");
        assertThat(live.exists("fresh")).isTrue();
        assertThat(text(cached.stylesheet("acme").orElseThrow())).isEqualTo("one");
        assertThat(cached.exists("fresh")).isFalse();

        cached.reload();
        assertThat(text(cached.stylesheet("acme").orElseThrow())).isEqualTo("two");
        assertThat(cached.exists("fresh")).isTrue();
    }

    @Test
    void copiedPagesAreCheckedAgainstTheBuiltInVersions() throws IOException {
        write(root.resolve("old/theme.json"), "{\"secureone\":\"0.1.0\"}");
        write(root.resolve("old/pages/login.html"), "<html></html>");
        write(root.resolve("current/theme.json"), "{\"secureone\":\">=0.1.1\"}");
        write(root.resolve("current/pages/login.html"), "<html></html>");
        write(root.resolve("current/pages/account/terms.html"), "<html></html>");
        write(root.resolve("unversioned/templates/consent.html"), "<html></html>");
        write(root.resolve("unversioned/templates/nothing.html"), "<html></html>");
        ThemeRegistry themes = registry(false);
        Map<String, Map<String, Object>> rows = new java.util.HashMap<>();
        themes.describe().forEach(r -> rows.put((String) r.get("name"), r));

        assertThat((List<?>) rows.get("old").get("outdated")).singleElement().asString().contains("pages/login.html");
        assertThat((List<?>) rows.get("current").get("outdated")).isEmpty();
        assertThat(rows.get("current").get("pages")).isEqualTo(List.of("account/terms.html", "login.html"));
        assertThat((List<?>) rows.get("unversioned").get("problems")).anySatisfy(p -> assertThat((String) p).contains("no \"secureone\" version"));
        assertThat((List<?>) rows.get("unversioned").get("problems")).anySatisfy(p -> assertThat((String) p).contains("nothing.html is not used"));
    }

    @Test
    void templatesPreferTheNearestTheme() throws IOException {
        write(root.resolve("corp/templates/consent.html"), "corp consent");
        write(root.resolve("acme/theme.json"), "{\"parent\":\"corp\"}");
        write(root.resolve("acme/templates/page.html"), "acme layout");
        ThemeRegistry themes = registry(false);
        assertThat(text(themes.template("acme", "consent").orElseThrow())).isEqualTo("acme layout");
        assertThat(text(themes.template("corp", "consent").orElseThrow())).isEqualTo("corp consent");
        assertThat(themes.template("corp", "oauth-error").orElseThrow().theme()).isEqualTo("base");
    }

    @Test
    void versionsCompareByNumbers() {
        assertThat(ThemeRegistry.compareVersions("0.1.10", "0.1.9")).isPositive();
        assertThat(ThemeRegistry.compareVersions("0.1.1", "0.1.1")).isZero();
        assertThat(ThemeRegistry.compareVersions("0.1", "0.1.0")).isZero();
        assertThat(ThemeRegistry.compareVersions("0.2.0-rc1", "0.10.0")).isNegative();
    }
}
