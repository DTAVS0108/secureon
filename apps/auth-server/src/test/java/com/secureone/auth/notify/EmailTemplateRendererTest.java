package com.secureone.auth.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailTemplateRendererTest {

    @Test
    void htmlBodyEscapesValuesSoANameCannotBecomeALink() {
        Map<String, String> vars = Map.of("userName", "<a href=\"https://evil.example\">Reset here</a>",
                "actionLink", "https://id.example.com/reset?token=abc&applicationId=1");
        String html = EmailTemplateRenderer.renderHtml("<p>Hi {{userName}}</p><a href=\"{{actionLink}}\">x</a>", vars);
        assertThat(html).doesNotContain("<a href=\"https://evil.example\">")
                .contains("&lt;a href=&quot;https://evil.example&quot;&gt;")
                .contains("href=\"https://id.example.com/reset?token=abc&amp;applicationId=1\"");
    }

    @Test
    void plainTextKeepsValuesAsTheyAre() {
        assertThat(EmailTemplateRenderer.render("Hi {{userName}}", Map.of("userName", "A & B"))).isEqualTo("Hi A & B");
    }
}
