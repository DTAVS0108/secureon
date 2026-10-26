package com.secureone.auth.audit.stream;

import com.secureone.auth.audit.AuditService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Platform settings › Logs & streaming (platform super admins; checked by the admin interceptor). */
@Tag(name = "Admin — log streams", description = "Audit log streaming to a SIEM and log retention")
@RestController
public class LogStreamController {

    private final LogStreamService streams;
    private final AuditService audit;

    public LogStreamController(LogStreamService streams, AuditService audit) {
        this.streams = streams;
        this.audit = audit;
    }

    @GetMapping("/api/admin/v1/settings/log-streams")
    public Map<String, Object> get() {
        return streams.getForAdmin();
    }

    @PutMapping("/api/admin/v1/settings/log-streams")
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        Map<String, Object> saved = streams.save(body);
        audit.record(null, "admin", "settings.log_streams_changed", "platform_setting", null, LogStreamService.SETTINGS_KEY, true);
        return saved;
    }

    @PostMapping("/api/admin/v1/settings/log-streams/{streamId}/test")
    public Map<String, Object> test(@PathVariable String streamId) {
        Map<String, Object> result = streams.test(streamId);
        return result != null ? result : Map.of("ok", false, "error", "No result");
    }
}
