package com.secureone.auth.oauth;

import com.secureone.auth.authn.TenantUserDetailsService;
import com.secureone.auth.config.oauth.OAuth2SigningKeyService;
import com.secureone.auth.config.oauth.SubjectClaims;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.user.UserAccount;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * OpenID Connect Back-Channel Logout 1.0, SecureOne as the provider: when a person signs out of SecureOne (or is
 * suspended or deleted), every application they signed in to that registered a back-channel logout URL receives
 * a signed {@code logout_token} by a direct server-to-server POST and ends its own session for that user.
 */
@Service
public class BackchannelLogoutService {

    private static final Logger log = LoggerFactory.getLogger(BackchannelLogoutService.class);
    static final String EVENT = "http://schemas.openid.net/event/backchannel-logout";

    private final JdbcTemplate jdbc;
    private final OAuthClientRepository clients;
    private final TenantUserDetailsService users;
    private final OAuth2SigningKeyService signingKeys;
    private final String issuer;
    private final RestClient http;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.secureone.auth.webhook.WebhookTargetPolicy targets;

    public BackchannelLogoutService(
            JdbcTemplate jdbc,
            OAuthClientRepository clients,
            TenantUserDetailsService users,
            OAuth2SigningKeyService signingKeys,
            @Value("${spring.security.oauth2.authorizationserver.issuer:http://localhost:9000}") String issuer) {
        this.jdbc = jdbc;
        this.clients = clients;
        this.users = users;
        this.signingKeys = signingKeys;
        this.issuer = issuer;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** The localhost addresses {@link #validateUri} accepts for development; anything else must be public. */
    static boolean isLocalDevelopment(String raw) {
        try {
            String host = new URI(raw).getHost();
            return host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("[::1]"));
        } catch (Exception e) {
            return false;
        }
    }

    /** Where a back-channel logout URL may point: https, or http on this computer for development. */
    public static String validateUri(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(raw.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Back-channel logout URL is not a valid address.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
        boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]");
        if (!("https".equals(scheme) || ("http".equals(scheme) && local)) || host.isEmpty() || uri.getFragment() != null) {
            throw new IllegalArgumentException("Back-channel logout URL must be an https:// address (http:// only for localhost), without #.");
        }
        return uri.toString();
    }

    /** Tells every application this principal ("tenant-slug:email") signed in to that they have signed out. */
    @Async
    public void signedOut(String principalName) {
        signedOut(principalName, null);
    }

    /** Same, naming the signed-out session ({@code sid} from the ID token) when it is known. */
    @Async
    public void signedOut(String principalName, String sessionId) {
        if (principalName == null || !principalName.contains(":")) {
            return;
        }
        try {
            RlsScope.runAsPlatform(() -> notifyClients(principalName,
                    SubjectClaims.resolve(principalName, users).map(UserAccount::getId), sessionId));
        } catch (RuntimeException e) {
            log.warn("Back-channel logout for {} not sent: {}", principalName, e.getMessage());
        }
    }

    /** Same, for an account (suspended, or deleted: its id is passed because the row may be gone). */
    @Async
    public void signedOut(String tenantSlug, String email, UUID userId) {
        if (tenantSlug == null || email == null) {
            return;
        }
        String principalName = tenantSlug + ":" + email;
        try {
            RlsScope.runAsPlatform(() -> notifyClients(principalName, Optional.ofNullable(userId), null));
        } catch (RuntimeException e) {
            log.warn("Back-channel logout for {} not sent: {}", principalName, e.getMessage());
        }
    }

    /**
     * Same, for applications found before the user's grants were removed (a suspended or deleted tenant: the
     * grants are deleted right away, so the lookup cannot wait for this to run).
     */
    @Async
    public void signedOutFrom(String principalName, UUID userId, List<OAuthClient> toNotify) {
        try {
            toNotify.forEach(c -> send(c, subjectFor(c, principalName, Optional.ofNullable(userId)), null));
        } catch (RuntimeException e) {
            log.warn("Back-channel logout for {} not sent: {}", principalName, e.getMessage());
        }
    }

    /**
     * Same, for one browser session that logged out ({@code sid}, or null when no single session ended), to
     * applications found before the grants were removed.
     */
    @Async
    public void sessionSignedOut(String principalName, String sessionId, List<OAuthClient> toNotify) {
        try {
            RlsScope.runAsPlatform(() -> {
                Optional<UUID> userId = SubjectClaims.resolve(principalName, users).map(UserAccount::getId);
                toNotify.forEach(c -> send(c, subjectFor(c, principalName, userId), sessionId));
            });
        } catch (RuntimeException e) {
            log.warn("Back-channel logout for {} not sent: {}", principalName, e.getMessage());
        }
    }

    /**
     * The applications to tell when this principal signs out: those holding grants for it, with a back-channel
     * logout URL. Loaded now, so the notice can go out after the grants (or the clients) are deleted.
     */
    public List<OAuthClient> clientsToNotify(String principalName) {
        List<OAuthClient> out = new java.util.ArrayList<>();
        for (String registeredId : jdbc.queryForList(
                "SELECT DISTINCT registered_client_id FROM platform.oauth2_authorization WHERE principal_name = ?",
                String.class, principalName)) {
            UUID id;
            try {
                id = UUID.fromString(registeredId);
            } catch (IllegalArgumentException e) {
                continue;
            }
            clients.findById(id)
                    .filter(c -> c.getBackchannelLogoutUri() != null && !c.getBackchannelLogoutUri().isBlank())
                    .filter(c -> !"DISABLED".equalsIgnoreCase(c.getStatus()))
                    .ifPresent(out::add);
        }
        return out;
    }

    private void notifyClients(String principalName, Optional<UUID> userId, String sessionId) {
        clientsToNotify(principalName).forEach(c -> send(c, subjectFor(c, principalName, userId), sessionId));
    }

    /** The {@code sub} this client sees in its ID tokens (see SubjectClaims). */
    static String subjectFor(OAuthClient client, String principalName, Optional<UUID> userId) {
        if (OAuthClient.SUBJECT_USER_ID.equals(client.getSubjectType()) && userId.isPresent()) {
            return userId.get().toString();
        }
        return principalName;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.secureone.auth.tenant.TenantIssuers tenantIssuers;

    String logoutToken(OAuthClient client, String subject, String sessionId) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(tenantIssuers != null ? tenantIssuers.preferredIssuerOfApplication(client.getApplicationId()) : issuer)
                .audience(List.of(client.getClientId()))
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(2)))
                .id(UUID.randomUUID().toString())
                .claim("events", Map.of(EVENT, Map.of()));
        if (sessionId != null && !sessionId.isBlank()) {
            builder.claim("sid", sessionId);
        }
        JwtClaimsSet claims = builder.build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).type("logout+jwt").build();
        return new NimbusJwtEncoder(signingKeys.signingJwkSource())
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private void send(OAuthClient client, String subject, String sessionId) {
        // Checked again at send time: the address may have started resolving to a private network since it was saved.
        String problem = targets != null ? targets.deliveryProblem(client.getBackchannelLogoutUri()) : null;
        if (problem != null && !isLocalDevelopment(client.getBackchannelLogoutUri())) {
            log.warn("Back-channel logout to client {} not sent: {}", client.getClientId(), problem);
            return;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("logout_token", logoutToken(client, subject, sessionId));
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                http.post()
                        .uri(client.getBackchannelLogoutUri())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Cache-Control", "no-store")
                        .body(form)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Back-channel logout sent to client {}", client.getClientId());
                return;
            } catch (RuntimeException e) {
                if (attempt == 2) {
                    log.warn("Back-channel logout to client {} failed: {}", client.getClientId(), e.getMessage());
                }
            }
        }
    }
}
