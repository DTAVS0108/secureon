package com.secureone.auth.notify;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailTemplateDefaultsTest {

    @Test
    void everyDefaultTemplateBuildsCompletely() {
        Map<String, Object> templates = EmailTemplateDefaults.platformDefaults();
        assertTrue(templates.containsKey("signup_account_exists"));
        assertTrue(templates.containsKey("signup_phone_in_use"));
        templates.forEach((key, value) -> {
            Map<?, ?> t = (Map<?, ?>) value;
            for (String field : new String[] {"subject", "bodyText", "bodyHtml"}) {
                String text = String.valueOf(t.get(field));
                assertFalse(text.isBlank() || "null".equals(text), key + "." + field + " is empty");
                assertFalse(text.contains("@@"), key + "." + field + " has an unreplaced placeholder");
            }
        });
    }
}
