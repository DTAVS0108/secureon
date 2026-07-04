package com.secureone.auth.oauth;

import com.secureone.auth.logging.ClientIpResolver;
import com.secureone.auth.logging.RequestContext;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenReuseAuditService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenReuseAuditService.class);

    public void recordReuse(OAuth2Authorization authorization, String presentedToken) {
        String clientId = authorization.getRegisteredClientId();
        String principal = authorization.getPrincipalName();
        String ip = ClientIpResolver.fromMdcOrUnknown();
        String requestId = MDC.get(RequestContext.REQUEST_ID);
        Map<String, Object> detail = new HashMap<>();
        detail.put("clientId", clientId);
        detail.put("principal", principal);
        detail.put("authorizationId", authorization.getId());
        detail.put("requestId", requestId);
        detail.put("presentedTokenPrefix", prefix(presentedToken));
        log.warn(
                "Refresh token reuse detected — authorization revoked [client={} principal={} ip={} req={}]",
                clientId,
                principal,
                ip,
                requestId);
    }

    private static String prefix(String token) {
        if (token == null || token.length() < 8) {
            return "—";
        }
        return token.substring(0, 8) + "…";
    }
}
