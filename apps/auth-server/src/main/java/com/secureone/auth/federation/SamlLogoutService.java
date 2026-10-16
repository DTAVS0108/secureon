package com.secureone.auth.federation;

import com.secureone.auth.account.AccountSessionService;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2AssertionAuthentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertion;
import org.springframework.security.saml2.provider.service.authentication.logout.OpenSaml5LogoutRequestValidator;
import org.springframework.security.saml2.provider.service.authentication.logout.OpenSaml5LogoutResponseValidator;
import org.springframework.security.saml2.provider.service.authentication.logout.Saml2LogoutRequest;
import org.springframework.security.saml2.provider.service.authentication.logout.Saml2LogoutRequestValidatorParameters;
import org.springframework.security.saml2.provider.service.authentication.logout.Saml2LogoutResponse;
import org.springframework.security.saml2.provider.service.authentication.logout.Saml2LogoutResponseValidatorParameters;
import org.springframework.security.saml2.provider.service.authentication.logout.Saml2LogoutValidatorResult;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;
import org.springframework.security.saml2.provider.service.web.authentication.logout.OpenSaml5LogoutRequestResolver;
import org.springframework.security.saml2.provider.service.web.authentication.logout.OpenSaml5LogoutResponseResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * SAML 2.0 single logout.
 * <ul>
 *   <li>SP-initiated: when a user who signed in through a SAML IdP signs out of SecureOne (OIDC
 *       {@code /connect/logout} or {@code /api/v1/auth/federation/saml/logout}), the local session ends and
 *       the browser takes a signed LogoutRequest to the IdP; the IdP's LogoutResponse comes back to the
 *       SLO endpoint, which continues to where the app asked to go.
 *   <li>IdP-initiated: the IdP sends a signed LogoutRequest (the user signed out elsewhere); every
 *       SecureOne session of the linked user ends and a LogoutResponse goes back.
 * </ul>
 */
@Service
public class SamlLogoutService {

    private static final Logger log = LoggerFactory.getLogger(SamlLogoutService.class);

    public static final String SESSION_PROVIDER = "secureone.saml.providerId";
    public static final String SESSION_NAME_ID = "secureone.saml.nameId";
    public static final String SESSION_INDEXES = "secureone.saml.sessionIndexes";
    public static final String SESSION_LOGOUT_CONTINUE = "secureone.saml.logoutContinue";
    public static final String SIGNED_OUT_PAGE = "/login.html?signedOut=1";

    private static final String PENDING_PREFIX = "federation:saml:logout:";
    private static final Pattern NAME_ID = Pattern.compile("<(?:[A-Za-z0-9_]+:)?NameID\\b[^>]*>([^<]+)</");
    private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
            "org.springframework.security.saml2.provider.service.authentication.logout.*;"
                    + "org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;"
                    + "java.lang.*;java.lang.Enum;java.util.*;!*");

    private final SamlRelyingParties registrations;
    private final StringRedisTemplate redis;
    private final UserIdentityRepository identities;
    private final UserAccountRepository users;
    private final TenantRepository tenants;
    private final AccountSessionService sessions;

    public SamlLogoutService(
            SamlRelyingParties registrations,
            StringRedisTemplate redis,
            UserIdentityRepository identities,
            UserAccountRepository users,
            TenantRepository tenants,
            AccountSessionService sessions) {
        this.registrations = registrations;
        this.redis = redis;
        this.identities = identities;
        this.users = users;
        this.tenants = tenants;
        this.sessions = sessions;
    }

    /** Called after a SAML sign-in: what single logout needs later lives on the session. */
    public void rememberSession(HttpServletRequest request, UUID providerId, SamlLoginService.SamlLogin login) {
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_PROVIDER, providerId.toString());
        session.setAttribute(SESSION_NAME_ID, login.nameId());
        session.setAttribute(SESSION_INDEXES, String.join("\n", login.sessionIndexes()));
    }

    /** True when this browser session came from a SAML IdP that takes part in single logout. */
    public boolean hasSamlSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(SESSION_PROVIDER) instanceof String providerId)) {
            return false;
        }
        RelyingPartyRegistration registration = registrations.findByRegistrationId(providerId);
        return registration != null && registrations.supportsLogout(registration);
    }

    /**
     * Ends the local session and, for a SAML session, sends the browser to the IdP with a LogoutRequest.
     * Where to go afterwards comes only from the session (set by the OIDC logout handler from a validated
     * post_logout_redirect_uri), never from the request, so this cannot be used as an open redirect.
     */
    public ResponseEntity<?> startLogout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        String continueUrl = session != null && session.getAttribute(SESSION_LOGOUT_CONTINUE) instanceof String c
                ? c
                : SIGNED_OUT_PAGE;
        String providerId = session != null && session.getAttribute(SESSION_PROVIDER) instanceof String p ? p : null;
        String nameId = session != null && session.getAttribute(SESSION_NAME_ID) instanceof String n ? n : null;
        String indexes = session != null && session.getAttribute(SESSION_INDEXES) instanceof String i ? i : "";
        endLocalSession(request);
        RelyingPartyRegistration registration = providerId == null ? null : registrations.findByRegistrationId(providerId);
        if (registration == null || nameId == null || !registrations.supportsLogout(registration)) {
            return redirect(continueUrl);
        }
        String relayState = UUID.randomUUID().toString().replace("-", "");
        OpenSaml5LogoutRequestResolver resolver = new OpenSaml5LogoutRequestResolver(registrations);
        resolver.setRelayStateResolver(r -> relayState);
        Saml2LogoutRequest logoutRequest = resolver.resolve(request, samlAuthentication(registration, nameId,
                indexes.isBlank() ? List.of() : List.of(indexes.split("\n"))));
        if (logoutRequest == null) {
            return redirect(continueUrl);
        }
        redis.opsForValue().set(PENDING_PREFIX + relayState, encode(new PendingLogout(logoutRequest, continueUrl)), Duration.ofMinutes(10));
        if (logoutRequest.getBinding() == Saml2MessageBinding.POST) {
            return autoPost(logoutRequest.getLocation(), "SAMLRequest", logoutRequest.getSamlRequest(), relayState);
        }
        return redirect(logoutRequest.getLocation() + (logoutRequest.getLocation().contains("?") ? "&" : "?")
                + logoutRequest.getParametersQuery());
    }

    /** The SLO endpoint: the IdP's LogoutResponse (to our request) or LogoutRequest (IdP-initiated). */
    public ResponseEntity<?> handle(UUID providerId, HttpServletRequest request, HttpServletResponse response) {
        RelyingPartyRegistration registration = registrations.findByRegistrationId(providerId.toString());
        if (registration == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "unknown_provider"));
        }
        Saml2MessageBinding binding = "POST".equalsIgnoreCase(request.getMethod())
                ? Saml2MessageBinding.POST
                : Saml2MessageBinding.REDIRECT;
        if (request.getParameter("SAMLResponse") != null) {
            return finishLogout(registration, binding, request);
        }
        if (request.getParameter("SAMLRequest") != null) {
            return idpInitiatedLogout(providerId, registration, binding, request, response);
        }
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_request", "message", "No SAML message."));
    }

    private ResponseEntity<?> finishLogout(RelyingPartyRegistration registration, Saml2MessageBinding binding, HttpServletRequest request) {
        String relayState = request.getParameter("RelayState");
        String raw = relayState == null ? null : redis.opsForValue().getAndDelete(PENDING_PREFIX + relayState);
        PendingLogout pending = decode(raw);
        if (pending == null) {
            // Local session already ended; nothing to continue to.
            return redirect(SIGNED_OUT_PAGE);
        }
        Saml2LogoutResponse logoutResponse = Saml2LogoutResponse.withRelyingPartyRegistration(registration)
                .samlResponse(request.getParameter("SAMLResponse"))
                .relayState(relayState)
                .binding(binding)
                .parameters(p -> copySignatureParameters(request, p, "SAMLResponse"))
                .build();
        Saml2LogoutValidatorResult result = new OpenSaml5LogoutResponseValidator().validate(
                new Saml2LogoutResponseValidatorParameters(logoutResponse, pending.request(), registration));
        if (result.hasErrors()) {
            log.warn("SAML LogoutResponse from {} rejected: {}", registration.getRegistrationId(), result.getErrors());
        }
        return redirect(pending.continueUrl());
    }

    private ResponseEntity<?> idpInitiatedLogout(
            UUID providerId,
            RelyingPartyRegistration registration,
            Saml2MessageBinding binding,
            HttpServletRequest request,
            HttpServletResponse response) {
        String samlRequest = request.getParameter("SAMLRequest");
        Optional<String> nameId = nameIdOf(samlRequest, binding);
        if (nameId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_request", "message", "LogoutRequest has no NameID."));
        }
        Saml2LogoutRequest logoutRequest = Saml2LogoutRequest.withRelyingPartyRegistration(registration)
                .samlRequest(samlRequest)
                .relayState(request.getParameter("RelayState"))
                .binding(binding)
                .parameters(p -> copySignatureParameters(request, p, "SAMLRequest"))
                .build();
        Authentication subject = samlAuthentication(registration, nameId.get(), List.of());
        Saml2LogoutValidatorResult result = new OpenSaml5LogoutRequestValidator()
                .validate(new Saml2LogoutRequestValidatorParameters(logoutRequest, registration, subject));
        if (result.hasErrors()) {
            log.warn("SAML LogoutRequest from {} rejected: {}", providerId, result.getErrors());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "invalid_request", "message", "LogoutRequest rejected."));
        }
        int ended = endUserSessions(providerId, nameId.get());
        log.info("SAML IdP-initiated logout via provider {}: {} session(s) ended", providerId, ended);
        Saml2LogoutResponse logoutResponse = new OpenSaml5LogoutResponseResolver(registrations).resolve(request, subject);
        if (logoutResponse == null) {
            return ResponseEntity.ok(Map.of("status", "signed_out", "sessions", ended));
        }
        if (logoutResponse.getBinding() == Saml2MessageBinding.POST) {
            return autoPost(logoutResponse.getResponseLocation(), "SAMLResponse", logoutResponse.getSamlResponse(),
                    logoutResponse.getRelayState());
        }
        return redirect(logoutResponse.getResponseLocation()
                + (logoutResponse.getResponseLocation().contains("?") ? "&" : "?") + logoutResponse.getParametersQuery());
    }

    /** Every SecureOne browser session of the user linked to this IdP NameID. */
    private int endUserSessions(UUID providerId, String nameId) {
        return identities.findByProviderIdAndProviderSubject(providerId, nameId)
                .flatMap(link -> users.findById(link.getUserId()))
                .flatMap(user -> tenants.findById(user.getTenantId())
                        .map(tenant -> tenant.getSlug() + ":" + user.getEmail()))
                .map(principal -> sessions.revokeOthers(principal, null))
                .orElse(0);
    }

    private static void endLocalSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private static Authentication samlAuthentication(RelyingPartyRegistration registration, String nameId, List<String> indexes) {
        DefaultSaml2AuthenticatedPrincipal principal = new DefaultSaml2AuthenticatedPrincipal(nameId, Map.of(), indexes);
        principal.setRelyingPartyRegistrationId(registration.getRegistrationId());
        Saml2ResponseAssertion assertion = Saml2ResponseAssertion.withResponseValue("")
                .nameId(nameId)
                .sessionIndexes(indexes)
                .build();
        return new Saml2AssertionAuthentication(principal, assertion, List.of(), registration.getRegistrationId());
    }

    private static void copySignatureParameters(HttpServletRequest request, Map<String, String> params, String messageParam) {
        params.put(messageParam, request.getParameter(messageParam));
        for (String name : List.of("RelayState", "SigAlg", "Signature")) {
            if (request.getParameter(name) != null) {
                params.put(name, request.getParameter(name));
            }
        }
    }

    /** NameID from a LogoutRequest (HTTP-Redirect messages are DEFLATE-compressed, HTTP-POST are not). */
    static Optional<String> nameIdOf(String samlRequest, Saml2MessageBinding binding) {
        try {
            byte[] decoded = Base64.getMimeDecoder().decode(samlRequest);
            String xml;
            if (binding == Saml2MessageBinding.REDIRECT) {
                Inflater inflater = new Inflater(true);
                inflater.setInput(decoded);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                while (!inflater.finished()) {
                    int n = inflater.inflate(buffer);
                    if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                        break;
                    }
                    out.write(buffer, 0, n);
                }
                inflater.end();
                xml = out.toString(StandardCharsets.UTF_8);
            } else {
                xml = new String(decoded, StandardCharsets.UTF_8);
            }
            Matcher m = NAME_ID.matcher(xml);
            return m.find() ? Optional.of(HtmlUtils.htmlUnescape(m.group(1).trim())) : Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static ResponseEntity<?> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", location).build();
    }

    private static ResponseEntity<?> autoPost(String location, String field, String value, String relayState) {
        String html = """
                <!DOCTYPE html><html><head><meta charset="utf-8"><title>Signing out…</title></head>
                <body onload="document.forms[0].submit()">
                <form method="post" action="%s">
                <input type="hidden" name="%s" value="%s">
                <input type="hidden" name="RelayState" value="%s">
                <noscript><button type="submit">Continue</button></noscript>
                </form></body></html>
                """
                .formatted(
                        HtmlUtils.htmlEscape(location),
                        field,
                        HtmlUtils.htmlEscape(value),
                        HtmlUtils.htmlEscape(relayState == null ? "" : relayState));
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).header("Cache-Control", "no-store").body(html);
    }

    record PendingLogout(Saml2LogoutRequest request, String continueUrl) implements java.io.Serializable {}

    private static String encode(PendingLogout pending) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(pending.request());
            out.writeObject(pending.continueUrl());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store the SAML logout request", ex);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static PendingLogout decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(raw)))) {
            in.setObjectInputFilter(FILTER);
            return new PendingLogout((Saml2LogoutRequest) in.readObject(), (String) in.readObject());
        } catch (IOException | ClassNotFoundException | ClassCastException | IllegalArgumentException ex) {
            return null;
        }
    }
}
