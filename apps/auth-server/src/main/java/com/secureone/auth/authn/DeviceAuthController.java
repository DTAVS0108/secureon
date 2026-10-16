package com.secureone.auth.authn;

import com.secureone.auth.config.oauth.DeviceCodeLookup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backs the hosted device page (RFC 8628): before approving, the user sees which application the code
 * on their screen belongs to. Rate-limited like the other code-checking endpoints.
 */
@Tag(name = "Public auth — device sign-in", description = "Device authorization grant (TVs, CLIs)")
@RestController
@RequestMapping("/api/v1/auth/device")
public class DeviceAuthController {

    private final DeviceCodeLookup devices;

    public DeviceAuthController(DeviceCodeLookup devices) {
        this.devices = devices;
    }

    @Operation(summary = "Look up a device user code", description = "Returns the application a pending code belongs to.")
    @PostMapping("/lookup")
    public ResponseEntity<Map<String, Object>> lookup(@RequestBody(required = false) Map<String, String> body) {
        String userCode = DeviceCodeLookup.normalize(body != null ? body.get("userCode") : null);
        return devices.pending(userCode)
                .<ResponseEntity<Map<String, Object>>>map(device -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("valid", true);
                    out.put("userCode", userCode);
                    out.put("applicationId", device.applicationId().toString());
                    out.put("applicationName", device.applicationName());
                    out.put("scopes", device.scopes());
                    return ResponseEntity.ok(out);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("valid", false, "detail", "This code is invalid or has expired.")));
    }
}
