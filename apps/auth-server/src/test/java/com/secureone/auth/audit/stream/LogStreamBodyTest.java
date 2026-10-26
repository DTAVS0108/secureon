package com.secureone.auth.audit.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LogStreamBodyTest {

    private final List<Map<String, Object>> entries = List.of(
            Map.of("id", "a", "time", "2026-10-26T10:00:00Z", "action", "user.login"),
            Map.of("id", "b", "time", "2026-10-26T10:00:01Z", "action", "user.logout"));

    @Test
    void jsonStreamsGetOneArrayPerBatch() throws Exception {
        String body = LogStreamService.body("json", entries);

        List<?> parsed = new ObjectMapper().readValue(body, List.class);
        assertThat(parsed).hasSize(2);
        assertThat(((Map<?, ?>) parsed.get(1)).get("action")).isEqualTo("user.logout");
    }

    @Test
    void splunkGetsConcatenatedHecEvents() throws Exception {
        String body = LogStreamService.body("splunk", entries);

        assertThat(body).startsWith("{\"time\":1793008800,").contains("}{\"time\":1793008801,");
        assertThat(body).contains("\"sourcetype\":\"secureone:audit\"").contains("\"event\":{");
    }
}
