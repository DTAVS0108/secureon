package com.secureone.auth.saml;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationAccessClaims;
import com.secureone.auth.application.ApplicationAccessService;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.application.TokenClaimMappings;
import com.secureone.auth.authn.AuthContextAttributes;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.tenant.Tenant;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.RequestAbstractType;
import org.opensaml.saml.saml2.core.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SecureOne as a SAML 2.0 identity provider, one per tenant ({@code <base>/saml2/idp/<slug>}): metadata, single
 * sign-on (SP-initiated over HTTP-Redirect or HTTP-POST, and IdP-initiated), and single logout. The user signs in
 * on the hosted sign-in page (with the application's methods and two-step rules); the service provider's
 * application decides who may use it, as for its OAuth clients. Responses go by HTTP-POST to the registered ACS
 * URL only.
 */
@Tag(name = "SAML identity provider", description = "SecureOne as a SAML 2.0 IdP for service providers")
@RestController
@RequestMapping("/saml2/idp")
public class SamlIdpController {

    private static final Logger log = LoggerFactory.getLogger(SamlIdpController.class);
    private static final String PENDING = "secureone.saml.pending";
    private static final Duration PENDING_LIFETIME = Duration.ofMinutes(15);
    /** How old an AuthnRequest may be (its IssueInstant), clock skew included. */
    private static final Duration REQUEST_MAX_AGE = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** An SSO waiting for the user to sign in (kept in the browser session). */
    record PendingSso(
            UUID serviceProviderId, UUID tenantId, String requestId, String relayState, String nameIdFormat,
            boolean forceAuthn, Instant createdAt) implements Serializable {}

    private final SamlServiceProviderService serviceProviders;
    private final SamlIdpCredentialService credentials;
    private final TenantRepository tenants;
    private final ApplicationRepository applications;
    private final UserAccountRepository users;
    private final ApplicationAccessService access;
    private final ApplicationAccessClaims accessClaims;
    private final ObjectProvider<com.secureone.auth.audit.AuditService> audit;
    private final ObjectProvider<com.secureone.auth.oauth.BackchannelLogoutService> backchannelLogout;
    private final String baseUrl;

    public SamlIdpController(
            SamlServiceProviderService serviceProviders,
            SamlIdpCredentialService credentials,
            TenantRepository tenants,
            ApplicationRepository applications,
            UserAccountRepository users,
            ApplicationAccessService access,
            ApplicationAccessClaims accessClaims,
            ObjectProvider<com.secureone.auth.audit.AuditService> audit,
            ObjectProvider<com.secureone.auth.oauth.BackchannelLogoutService> backchannelLogout,
            @Value("${secureone.public-base-url:${spring.security.oauth2.authorizationserver.issuer:http://localhost:9000}}") String baseUrl) {
        this.serviceProviders = serviceProviders;
        this.credentials = credentials;
        this.tenants = tenants;
        this.applications = applications;
        this.users = users;
        this.access = access;
        this.accessClaims = accessClaims;
        this.audit = audit;
        this.backchannelLogout = backchannelLogout;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String entityId(String slug) {
        return baseUrl + "/saml2/idp/" + slug;
    }

    String ssoUrl(String slug) {
        return entityId(slug) + "/sso";
    }

    String sloUrl(String slug) {
        return entityId(slug) + "/slo";
    }

    // ---- metadata ----

    @Operation(summary = "The tenant IdP's SAML metadata (entity ID, SSO and SLO endpoints, signing certificate)")
    @GetMapping(value = "/{slug}/metadata", produces = "application/samlmetadata+xml")
    public String metadata(@PathVariable String slug, HttpServletResponse response) {
        Tenant tenant = activeTenant(slug).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "No such identity provider."));
        response.setHeader("Content-Disposition", "inline; filename=\"" + slug + "-idp-metadata.xml\"");
        String certificate = SamlIdpCredentialService.base64(credentials.credential(tenant.getId()).certificate());
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<md:EntityDescriptor xmlns:md=\"urn:oasis:names:tc:SAML:2.0:metadata\" xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\" entityID=\"")
                .append(escape(entityId(slug))).append("\">\n")
                .append("  <md:IDPSSODescriptor WantAuthnRequestsSigned=\"false\" protocolSupportEnumeration=\"urn:oasis:names:tc:SAML:2.0:protocol\">\n")
                .append("    <md:KeyDescriptor use=\"signing\"><ds:KeyInfo><ds:X509Data><ds:X509Certificate>")
                .append(certificate).append("</ds:X509Certificate></ds:X509Data></ds:KeyInfo></md:KeyDescriptor>\n");
        for (String binding : List.of(SamlIdpMessages.BINDING_REDIRECT, SamlIdpMessages.BINDING_POST)) {
            xml.append("    <md:SingleLogoutService Binding=\"").append(binding).append("\" Location=\"")
                    .append(escape(sloUrl(slug))).append("\"/>\n");
        }
        for (String format : List.of("email", "persistent", "unspecified")) {
            xml.append("    <md:NameIDFormat>").append(SamlServiceProviderService.NAME_ID_FORMATS.get(format)).append("</md:NameIDFormat>\n");
        }
        for (String binding : List.of(SamlIdpMessages.BINDING_REDIRECT, SamlIdpMessages.BINDING_POST)) {
            xml.append("    <md:SingleSignOnService Binding=\"").append(binding).append("\" Location=\"")
                    .append(escape(ssoUrl(slug))).append("\"/>\n");
        }
        xml.append("  </md:IDPSSODescriptor>\n</md:EntityDescriptor>\n");
        return xml.toString();
    }

    // ---- single sign-on ----

    @Operation(
            summary = "Single sign-on: an SP's AuthnRequest (HTTP-Redirect: GET, HTTP-POST: form POST)",
            description = "The user signs in if needed; the signed Response is POSTed to the SP's registered ACS URL.")
    @RequestMapping(value = "/{slug}/sso", method = {RequestMethod.GET, RequestMethod.POST})
    public void sso(@PathVariable String slug,
            @RequestParam(value = "SAMLRequest", required = false) String samlRequest,
            @RequestParam(value = "RelayState", required = false) String relayState,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<Tenant> tenant = activeTenant(slug);
        if (tenant.isEmpty()) {
            page(response, 404, "No such identity provider", "This sign-in link is not valid.");
            return;
        }
        if (samlRequest == null || samlRequest.isBlank()) {
            page(response, 400, "Sign-in request missing", "The application did not send a SAML request.");
            return;
        }
        boolean redirect = "GET".equalsIgnoreCase(request.getMethod());
        RequestAbstractType message;
        try {
            message = SamlIdpMessages.decodeRequest(samlRequest, redirect);
        } catch (IllegalArgumentException e) {
            page(response, 400, "Sign-in request not valid", e.getMessage());
            return;
        }
        if (!(message instanceof AuthnRequest authn)) {
            page(response, 400, "Sign-in request not valid", "Send logout requests to " + sloUrl(slug) + ".");
            return;
        }
        String issuer = authn.getIssuer() != null ? authn.getIssuer().getValue() : null;
        Optional<SamlServiceProviderService.ServiceProvider> found =
                serviceProviders.byEntityId(tenant.get().getId(), issuer).filter(SamlServiceProviderService.ServiceProvider::active);
        if (found.isEmpty()) {
            log.info("SAML AuthnRequest from unknown service provider {} for tenant {}", issuer, slug);
            page(response, 400, "Unknown application", "This application is not set up to sign in with this organization.");
            return;
        }
        SamlServiceProviderService.ServiceProvider sp = found.get();
        String problem = requestProblem(sp, authn, redirect, request, slug);
        if (problem != null) {
            page(response, 400, "Sign-in request refused", problem);
            return;
        }
        String format = sp.nameIdFormat();
        String requested = authn.getNameIDPolicy() != null ? authn.getNameIDPolicy().getFormat() : null;
        if (requested != null && !requested.equals(SamlServiceProviderService.NAME_ID_FORMATS.get("unspecified"))) {
            Optional<String> match = SamlServiceProviderService.NAME_ID_FORMATS.entrySet().stream()
                    .filter(e -> e.getValue().equals(requested)).map(Map.Entry::getKey).findFirst();
            if (match.isEmpty()) {
                postError(response, tenant.get(), sp, authn.getID(), relayState, StatusCode.INVALID_NAMEID_POLICY,
                        "Name ID format " + requested + " is not supported.");
                return;
            }
            format = match.get();
        }
        boolean force = Boolean.TRUE.equals(authn.isForceAuthn());
        if (Boolean.TRUE.equals(authn.isPassive()) && (force || signedInUser(tenant.get()).isEmpty())) {
            postError(response, tenant.get(), sp, authn.getID(), relayState, StatusCode.NO_PASSIVE, "The user is not signed in.");
            return;
        }
        if (force) {
            // ForceAuthn: sign in again, whatever this browser's session.
            HttpSession old = request.getSession(false);
            if (old != null) {
                old.invalidate();
            }
            SecurityContextHolder.clearContext();
        }
        startPending(request, response, new PendingSso(sp.id(), tenant.get().getId(), authn.getID(), relayState, format, force, Instant.now()));
    }

    @Operation(
            summary = "IdP-initiated sign-in to a service provider",
            description = "sp: the SP's entity ID (or its id). The user signs in if needed; the Response has no InResponseTo.")
    @GetMapping("/{slug}/init")
    public void init(@PathVariable String slug, @RequestParam("sp") String spRef,
            @RequestParam(value = "RelayState", required = false) String relayState,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<Tenant> tenant = activeTenant(slug);
        Optional<SamlServiceProviderService.ServiceProvider> sp = tenant.flatMap(t -> serviceProviders.byEntityId(t.getId(), spRef)
                .or(() -> parseUuid(spRef).flatMap(serviceProviders::byId).filter(s -> s.tenantId().equals(t.getId()))))
                .filter(SamlServiceProviderService.ServiceProvider::active);
        if (sp.isEmpty()) {
            page(response, 404, "Unknown application", "This application is not set up to sign in with this organization.");
            return;
        }
        startPending(request, response,
                new PendingSso(sp.get().id(), tenant.get().getId(), null, relayState, sp.get().nameIdFormat(), false, Instant.now()));
    }

    @Operation(summary = "After sign-in: the Response for the waiting SSO (POSTed to the SP)")
    @GetMapping("/resume")
    public void resume(@RequestParam("ref") String ref, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        PendingSso pending = session == null ? null : pendingMap(session).get(ref);
        if (pending == null || pending.createdAt().plus(PENDING_LIFETIME).isBefore(Instant.now())) {
            page(response, 400, "Sign-in expired", "Go back to the application and sign in again.");
            return;
        }
        Optional<SamlServiceProviderService.ServiceProvider> found =
                serviceProviders.byId(pending.serviceProviderId()).filter(SamlServiceProviderService.ServiceProvider::active);
        Optional<Tenant> tenant = RlsScope.asPlatform(() -> tenants.findById(pending.tenantId()))
                .filter(t -> "ACTIVE".equalsIgnoreCase(t.getStatus()));
        if (found.isEmpty() || tenant.isEmpty()) {
            forget(session, ref);
            page(response, 400, "Application unavailable", "This application can no longer sign in here.");
            return;
        }
        SamlServiceProviderService.ServiceProvider sp = found.get();
        Optional<UserAccount> user = signedInUser(tenant.get());
        Instant signedInAt = AuthContextAttributes.authTime(request);
        if (user.isEmpty() || (pending.forceAuthn() && (signedInAt == null || signedInAt.isBefore(pending.createdAt())))) {
            sendToSignIn(response, ref, sp.applicationId());
            return;
        }
        forget(session, ref);
        Optional<Application> app = RlsScope.asPlatform(() -> applications.findById(sp.applicationId()))
                .filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus()));
        if (app.isEmpty() || !"ACTIVE".equalsIgnoreCase(user.get().getStatus())
                || !RlsScope.asPlatform(() -> access.admit(sp.applicationId(), user.get().getId()))) {
            log.info("SAML SSO to {} denied for user {}", sp.entityId(), user.get().getId());
            postError(response, tenant.get(), sp, pending.requestId(), pending.relayState(), StatusCode.REQUEST_DENIED,
                    "This account has not been granted access to this application.");
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean twoStep = AuthContextAttributes.amrOf(auth).contains("mfa");
        SamlIdpMessages.Assertion assertion = new SamlIdpMessages.Assertion(
                entityId(tenant.get().getSlug()), sp.entityId(), sp.acsUrl(), pending.requestId(),
                SamlServiceProviderService.NAME_ID_FORMATS.get(pending.nameIdFormat()),
                "persistent".equals(pending.nameIdFormat()) ? user.get().getId().toString() : user.get().getEmail(),
                signedInAt, sessionIndex(session), twoStep,
                attributes(sp, user.get(), tenant.get(), app.get()), sp.assertionLifetimeSeconds());
        String samlResponse = SamlIdpMessages.successResponse(
                assertion, credentials.credential(tenant.get().getId()), sp.signResponse(), sp.signAssertion());
        audit.ifAvailable(a -> a.recordIndependently(tenant.get().getId(), sp.applicationId(), user.get().getEmail(),
                "user.saml_sso", "saml_service_provider", sp.id(), sp.name(), true));
        autoPost(response, sp.acsUrl(), "SAMLResponse", samlResponse, pending.relayState());
    }

    // ---- single logout ----

    @Operation(
            summary = "Single logout: an SP's LogoutRequest (HTTP-Redirect or HTTP-POST)",
            description = "Signs this browser out of SecureOne (apps with back-channel logout are told) and POSTs a signed LogoutResponse to the SP's SLO URL.")
    @RequestMapping(value = "/{slug}/slo", method = {RequestMethod.GET, RequestMethod.POST})
    public void slo(@PathVariable String slug,
            @RequestParam(value = "SAMLRequest", required = false) String samlRequest,
            @RequestParam(value = "SAMLResponse", required = false) String samlResponse,
            @RequestParam(value = "RelayState", required = false) String relayState,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<Tenant> tenant = activeTenant(slug);
        if (tenant.isEmpty()) {
            page(response, 404, "No such identity provider", "This sign-out link is not valid.");
            return;
        }
        if (samlRequest == null || samlRequest.isBlank()) {
            // A LogoutResponse (or nothing): this browser is signed out already.
            page(response, 200, "Signed out", "You are signed out.");
            return;
        }
        boolean redirect = "GET".equalsIgnoreCase(request.getMethod());
        RequestAbstractType message;
        try {
            message = SamlIdpMessages.decodeRequest(samlRequest, redirect);
        } catch (IllegalArgumentException e) {
            page(response, 400, "Sign-out request not valid", e.getMessage());
            return;
        }
        if (!(message instanceof LogoutRequest logout)) {
            page(response, 400, "Sign-out request not valid", "Expected a LogoutRequest.");
            return;
        }
        String issuer = logout.getIssuer() != null ? logout.getIssuer().getValue() : null;
        Optional<SamlServiceProviderService.ServiceProvider> sp = serviceProviders.byEntityId(tenant.get().getId(), issuer);
        if (sp.isEmpty()) {
            page(response, 400, "Unknown application", "This application is not set up with this organization.");
            return;
        }
        String problem = signatureProblem(sp.get(), logout, redirect, request);
        if (problem != null) {
            page(response, 400, "Sign-out request refused", problem);
            return;
        }
        Optional<UserAccount> user = signedInUser(tenant.get());
        HttpSession session = request.getSession(false);
        if (user.isPresent() && session != null) {
            String principal = SecurityContextHolder.getContext().getAuthentication().getName();
            session.invalidate();
            SecurityContextHolder.clearContext();
            backchannelLogout.ifAvailable(b -> b.signedOut(principal));
            audit.ifAvailable(a -> a.recordIndependently(tenant.get().getId(), sp.get().applicationId(), user.get().getEmail(),
                    "user.saml_logout", "saml_service_provider", sp.get().id(), sp.get().name(), true));
        }
        if (sp.get().sloUrl() == null) {
            page(response, 200, "Signed out", "You are signed out.");
            return;
        }
        String logoutResponse = SamlIdpMessages.logoutResponse(entityId(slug), sp.get().sloUrl(), logout.getID(),
                credentials.credential(tenant.get().getId()));
        autoPost(response, sp.get().sloUrl(), "SAMLResponse", logoutResponse, relayState);
    }

    // ---- helpers ----

    /** Why the AuthnRequest is refused (null: it is fine). */
    private String requestProblem(SamlServiceProviderService.ServiceProvider sp, AuthnRequest authn, boolean redirect,
            HttpServletRequest request, String slug) {
        String signature = signatureProblem(sp, authn, redirect, request);
        if (signature != null) {
            return signature;
        }
        String acs = authn.getAssertionConsumerServiceURL();
        if (acs != null && !acs.equals(sp.acsUrl())) {
            // Never send an assertion anywhere but the registered ACS URL.
            return "The request asks for an assertion consumer URL that is not registered for this application.";
        }
        if (authn.getAssertionConsumerServiceIndex() != null && acs == null) {
            return "Assertion consumer service indexes are not supported: send the ACS URL.";
        }
        String binding = authn.getProtocolBinding();
        if (binding != null && !binding.equals(SamlIdpMessages.BINDING_POST)) {
            return "Responses are sent with the HTTP-POST binding only.";
        }
        String destination = authn.getDestination();
        if (destination != null && !destination.equals(ssoUrl(slug))) {
            return "The request was meant for another identity provider (" + destination + ").";
        }
        Instant issued = authn.getIssueInstant();
        if (issued == null || issued.isBefore(Instant.now().minus(REQUEST_MAX_AGE))
                || issued.isAfter(Instant.now().plus(Duration.ofMinutes(2)))) {
            return "The request is too old (or its clock is off): try again.";
        }
        return null;
    }

    /** A request signature: checked when present (and the SP's certificate is known), required when configured. */
    private static String signatureProblem(SamlServiceProviderService.ServiceProvider sp, RequestAbstractType message,
            boolean redirect, HttpServletRequest request) {
        boolean signed = redirect ? request.getParameter("Signature") != null : message.getSignature() != null;
        if (!signed) {
            return sp.requireSignedRequests() ? "This application must sign its requests." : null;
        }
        if (sp.spCertificate() == null) {
            // Signed, but no certificate to check it with: treated as unsigned.
            return sp.requireSignedRequests() ? "This application must sign its requests." : null;
        }
        java.security.cert.X509Certificate certificate = SamlIdpCredentialService.parseCertificate(sp.spCertificate());
        boolean valid = redirect
                ? SamlIdpMessages.redirectSignatureValid(request.getQueryString(), certificate)
                : SamlIdpMessages.postSignatureValid(message, certificate);
        return valid ? null : "The request's signature does not match this application's certificate.";
    }

    private void startPending(HttpServletRequest request, HttpServletResponse response, PendingSso pending)
            throws IOException {
        HttpSession session = request.getSession(true);
        Map<String, PendingSso> map = pendingMap(session);
        map.values().removeIf(p -> p.createdAt().plus(PENDING_LIFETIME).isBefore(Instant.now()));
        while (map.size() >= 10) {
            map.remove(map.keySet().iterator().next());
        }
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String ref = HexFormat.of().formatHex(bytes);
        map.put(ref, pending);
        session.setAttribute(PENDING, map);
        response.sendRedirect("/saml2/idp/resume?ref=" + ref);
    }

    /** Removes a pending SSO (set again so a shared session store saves the change). */
    private static void forget(HttpSession session, String ref) {
        Map<String, PendingSso> map = pendingMap(session);
        map.remove(ref);
        session.setAttribute(PENDING, map);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, PendingSso> pendingMap(HttpSession session) {
        Object raw = session.getAttribute(PENDING);
        return raw instanceof LinkedHashMap<?, ?> map ? (Map<String, PendingSso>) map : new LinkedHashMap<>();
    }

    private static void sendToSignIn(HttpServletResponse response, String ref, UUID applicationId) throws IOException {
        String back = "/saml2/idp/resume?ref=" + ref;
        response.sendRedirect("/login.html?continue=" + URLEncoder.encode(back, StandardCharsets.UTF_8)
                + "&applicationId=" + applicationId);
    }

    /** The browser's signed-in user, when it is a user of this tenant ("slug:email" principal). */
    private Optional<UserAccount> signedInUser(Tenant tenant) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        String name = auth.getName();
        int sep = name == null ? -1 : name.indexOf(':');
        if (sep <= 0 || !name.substring(0, sep).equalsIgnoreCase(tenant.getSlug())) {
            return Optional.empty();
        }
        String email = name.substring(sep + 1).trim().toLowerCase(Locale.ROOT);
        return RlsScope.asPlatform(() -> users.findByTenantIdAndEmail(tenant.getId(), email));
    }

    private Optional<Tenant> activeTenant(String slug) {
        return RlsScope.asPlatform(() -> tenants.findBySlug(slug == null ? "" : slug.toLowerCase(Locale.ROOT)))
                .filter(t -> "ACTIVE".equalsIgnoreCase(t.getStatus()));
    }

    /** The SP's attributes for this user; an attribute with no value is left out. */
    private Map<String, List<String>> attributes(SamlServiceProviderService.ServiceProvider sp, UserAccount user,
            Tenant tenant, Application app) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        ApplicationAccessClaims.Access roles = null;
        for (SamlServiceProviderService.Attribute attribute : sp.effectiveAttributes()) {
            List<String> values = new ArrayList<>();
            switch (attribute.source()) {
                case "roles", "permissions" -> {
                    if (roles == null) {
                        roles = accessClaims.forApplication(app, user.getId());
                    }
                    values.addAll("roles".equals(attribute.source()) ? roles.roles() : roles.permissions());
                }
                case "user_id" -> values.add(user.getId().toString());
                default -> {
                    Object value = TokenClaimMappings.valueFor(new TokenClaimMappings.Claim(
                            attribute.name(), attribute.source(), attribute.value(), true, true), user, tenant.getSlug());
                    if (value instanceof java.util.Collection<?> many) {
                        many.forEach(v -> values.add(String.valueOf(v)));
                    } else if (value != null && !String.valueOf(value).isBlank()) {
                        values.add(String.valueOf(value));
                    }
                }
            }
            if (!values.isEmpty()) {
                out.put(attribute.name(), values);
            }
        }
        return out;
    }

    private void postError(HttpServletResponse response, Tenant tenant, SamlServiceProviderService.ServiceProvider sp,
            String inResponseTo, String relayState, String subStatus, String message) throws IOException {
        String samlResponse = SamlIdpMessages.errorResponse(entityId(tenant.getSlug()), sp.acsUrl(), inResponseTo, subStatus,
                message, credentials.credential(tenant.getId()));
        autoPost(response, sp.acsUrl(), "SAMLResponse", samlResponse, relayState);
    }

    /** A page that POSTs the message to the SP at once (HTTP-POST binding), with a button when scripts are off. */
    private static void autoPost(HttpServletResponse response, String action, String field, String value, String relayState)
            throws IOException {
        byte[] nonceBytes = new byte[16];
        RANDOM.nextBytes(nonceBytes);
        String nonce = Base64.getEncoder().encodeToString(nonceBytes);
        response.setStatus(200);
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Security-Policy",
                "default-src 'none'; script-src 'nonce-" + nonce + "'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'");
        response.setHeader("Referrer-Policy", "no-referrer");
        StringBuilder html = new StringBuilder()
                .append("<!doctype html><html><head><meta charset=\"utf-8\"><title>Signing in</title></head><body>")
                .append("<form method=\"post\" action=\"").append(escape(action)).append("\">")
                .append("<input type=\"hidden\" name=\"").append(field).append("\" value=\"").append(escape(value)).append("\">");
        if (relayState != null && !relayState.isEmpty()) {
            html.append("<input type=\"hidden\" name=\"RelayState\" value=\"").append(escape(relayState)).append("\">");
        }
        html.append("<noscript><p>Continue to the application:</p><button type=\"submit\">Continue</button></noscript></form>")
                .append("<script nonce=\"").append(nonce).append("\">document.forms[0].submit();</script></body></html>");
        response.getWriter().write(html.toString());
    }

    private static void page(HttpServletResponse response, int status, String title, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'");
        response.getWriter().write("<!doctype html><html><head><meta charset=\"utf-8\"><title>" + escape(title)
                + "</title></head><body style=\"font-family:system-ui,sans-serif;max-width:32rem;margin:4rem auto;padding:0 1rem\"><h1>"
                + escape(title) + "</h1><p>" + escape(message) + "</p></body></html>");
    }

    /** A stable, opaque session index for this browser session (never the session ID itself). */
    private static String sessionIndex(HttpSession session) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(session.getId().getBytes(StandardCharsets.UTF_8));
            return "_" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Optional<UUID> parseUuid(String raw) {
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
