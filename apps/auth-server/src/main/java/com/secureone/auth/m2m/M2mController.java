package com.secureone.auth.m2m;

import com.secureone.auth.serviceaccount.ServiceAccountContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/m2m")
public class M2mController {

    @GetMapping("/whoami")
    public Map<String, Object> whoami() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication.getPrincipal() instanceof ServiceAccountContext context)) {
            throw new IllegalStateException("M2M authentication required.");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tenantId", context.tenantId().toString());
        out.put("serviceAccountId", context.serviceAccountId().toString());
        out.put("name", context.name());
        return out;
    }
}
