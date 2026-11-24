package com.secureone.auth.session;

import com.secureone.auth.user.UserAccount;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LoginHistoryService {

    private final LoginHistoryRepository repository;

    public LoginHistoryService(LoginHistoryRepository repository) {
        this.repository = repository;
    }

    public void record(
            UserAccount user, String result, String ip, String device, String method, String location) {
        record(user, null, result, ip, device, method, location, null, null);
    }

    public void record(
            UserAccount user,
            UUID applicationId,
            String result,
            String ip,
            String device,
            String method,
            String location) {
        record(user, applicationId, result, ip, device, method, location, null, null);
    }

    public void record(
            UserAccount user,
            UUID applicationId,
            String result,
            String ip,
            String device,
            String method,
            String location,
            String sessionId,
            String failureReason) {
        LoginHistory row = new LoginHistory();
        row.setTenantId(user.getTenantId());
        row.setApplicationId(applicationId);
        row.setUserId(user.getId());
        row.setResult(result);
        row.setIp(ip != null ? ip : "unknown");
        row.setDevice(device != null ? device : "unknown");
        row.setSessionId(sessionId);
        Map<String, Object> geo = new HashMap<>();
        geo.put("location", location != null ? location : "—");
        geo.put("method", method);
        geo.put("actorEmail", user.getLoginKey());
        if (failureReason != null) {
            geo.put("failureReason", failureReason);
        }
        row.setGeo(geo);
        repository.save(row);
    }

    public void recordFailure(
            UserAccount user,
            UUID applicationId,
            String ip,
            String device,
            String sessionId,
            String reasonCode,
            String failureDetail,
            Map<String, Object> diagnostics) {
        LoginHistory row = new LoginHistory();
        row.setTenantId(user.getTenantId());
        row.setApplicationId(applicationId);
        row.setUserId(user.getId());
        row.setResult("FAILURE");
        row.setIp(ip != null ? ip : "unknown");
        row.setDevice(device != null ? device : "unknown");
        row.setSessionId(sessionId);
        Map<String, Object> geo = new HashMap<>();
        geo.put("location", "—");
        geo.put("method", "password");
        geo.put("actorEmail", user.getLoginKey());
        geo.put("failureReason", reasonCode);
        if (failureDetail != null) {
            geo.put("failureDetail", failureDetail);
        }
        if (diagnostics != null && !diagnostics.isEmpty()) {
            geo.put("diagnostics", diagnostics);
        }
        row.setGeo(geo);
        repository.save(row);
    }
}
