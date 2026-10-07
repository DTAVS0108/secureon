package com.secureone.auth.oauth;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.logging.ClientIpResolver;
import com.secureone.auth.logging.RequestContext;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.stereotype.Service;

/** Records refresh-token reuse (a likely stolen token) in the log and the tenant's audit trail. */
@Service
public class RefreshTokenReuseAuditService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenReuseAuditService.class);
    static final String ACTION = "oauth.refresh_token_reuse";

    private final AuditService audit;
    private final OAuthClientRepository oauthClients;
    private final ApplicationRepository applications;

    public RefreshTokenReuseAuditService(
            AuditService audit, OAuthClientRepository oauthClients, ApplicationRepository applications) {
        this.audit = audit;
        this.oauthClients = oauthClients;
        this.applications = applications;
    }

    public void recordReuse(OAuth2Authorization authorization, String presentedToken) {
        String registeredClientId = authorization.getRegisteredClientId();
        String principal = authorization.getPrincipalName();
        log.warn(
                "Refresh token reuse detected — authorization revoked [client={} principal={} ip={} req={} token={}]",
                registeredClientId,
                principal,
                ClientIpResolver.fromMdcOrUnknown(),
                MDC.get(RequestContext.REQUEST_ID),
                prefix(presentedToken));

        Optional<OAuthClient> client = parseUuid(registeredClientId).flatMap(oauthClients::findById);
        Optional<Application> app = client.flatMap(c -> applications.findById(c.getApplicationId()));
        audit.record(
                app.map(Application::getTenantId).orElse(null),
                app.map(Application::getId).orElse(null),
                principal,
                ACTION,
                "oauth_client",
                client.map(OAuthClient::getId).orElse(null),
                client.map(OAuthClient::getClientId).orElse(registeredClientId),
                false);
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException ex) {
            return Optional.empty();
        }
    }

    private static String prefix(String token) {
        if (token == null || token.length() < 8) {
            return "—";
        }
        return token.substring(0, 8) + "…";
    }
}
