package com.secureone.auth.federation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.AbstractSaml2AuthenticationRequest;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationException;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationToken;
import org.springframework.security.saml2.provider.service.authentication.Saml2PostAuthenticationRequest;
import org.springframework.security.saml2.provider.service.authentication.Saml2RedirectAuthenticationRequest;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertionAccessor;
import org.springframework.security.saml2.provider.service.metadata.OpenSaml5MetadataResolver;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.web.OpenSaml5AuthenticationTokenConverter;
import org.springframework.security.saml2.provider.service.web.authentication.OpenSaml5AuthenticationRequestResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * SAML 2.0 Web SSO as a service provider: builds the AuthnRequest for a provider (HTTP-Redirect or
 * HTTP-POST binding), and validates the IdP's response (signature, audience, recipient, time window,
 * InResponseTo) with Spring Security's OpenSAML provider before the profile is used to sign in.
 */
@Service
public class SamlLoginService {

    /** Attributes IdPs commonly put the email in (SAML, WS-Federation claim and LDAP-style names). */
    private static final List<String> EMAIL_ATTRIBUTES = List.of(
            "email",
            "mail",
            "emailAddress",
            "urn:oid:0.9.2342.19200300.100.1.3",
            "urn:oid:1.2.840.113549.1.9.1",
            "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress");

    private final SamlRelyingParties registrations;
    private final SamlRequestStore requests;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    private final OpenSaml5AuthenticationProvider authenticationProvider = new OpenSaml5AuthenticationProvider();
    private final OpenSaml5MetadataResolver metadataResolver = new OpenSaml5MetadataResolver();

    public SamlLoginService(
            SamlRelyingParties registrations,
            SamlRequestStore requests,
            org.springframework.data.redis.core.StringRedisTemplate redis) {
        this.registrations = registrations;
        this.requests = requests;
        this.redis = redis;
        this.metadataResolver.setUsePrettyPrint(true);
        // Tell the IdP what we do and expect (IdPs import these flags from SP metadata).
        this.metadataResolver.setEntityDescriptorCustomizer(parameters -> {
            var sp = parameters.getEntityDescriptor()
                    .getSPSSODescriptor(org.opensaml.saml.common.xml.SAMLConstants.SAML20P_NS);
            if (sp != null) {
                sp.setAuthnRequestsSigned(parameters.getRelyingPartyRegistration().isAuthnRequestsSigned());
                sp.setWantAssertionsSigned(true);
            }
        });
    }

    /** Sends the browser to the IdP with an AuthnRequest whose RelayState is our federation state. */
    public ResponseEntity<?> start(
            IdentityProvider provider, String state, HttpServletRequest request, HttpServletResponse response) {
        OpenSaml5AuthenticationRequestResolver resolver = new OpenSaml5AuthenticationRequestResolver(registrations);
        resolver.setRequestMatcher(forRegistration(provider.getId()));
        resolver.setRelayStateResolver(r -> state);
        AbstractSaml2AuthenticationRequest authnRequest = resolver.resolve(request);
        if (authnRequest == null) {
            throw new IllegalStateException("SAML provider is not configured.");
        }
        requests.saveAuthenticationRequest(authnRequest, request, response);
        if (authnRequest instanceof Saml2PostAuthenticationRequest post) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .header("Cache-Control", "no-store")
                    .body(autoPostForm(post));
        }
        Saml2RedirectAuthenticationRequest redirect = (Saml2RedirectAuthenticationRequest) authnRequest;
        StringBuilder url = new StringBuilder(redirect.getAuthenticationRequestUri());
        url.append(url.indexOf("?") >= 0 ? "&" : "?");
        url.append("SAMLRequest=").append(enc(redirect.getSamlRequest()));
        url.append("&RelayState=").append(enc(redirect.getRelayState()));
        if (redirect.getSigAlg() != null && redirect.getSignature() != null) {
            url.append("&SigAlg=").append(enc(redirect.getSigAlg()));
            url.append("&Signature=").append(enc(redirect.getSignature()));
        }
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", url.toString()).build();
    }

    /** Who signed in, plus what single logout needs later (NameID and the IdP's session indexes). */
    public record SamlLogin(
            IdentityProviderService.FederationUserProfile profile, String nameId, List<String> sessionIndexes) {}

    /**
     * Validates the IdP's response for this provider and returns who signed in. Each response is accepted
     * once (replay cache keyed by its hash, kept for the assertion lifetime Spring allows), which matters
     * most for IdP-initiated sign-in where there is no AuthnRequest to bind it to.
     */
    public SamlLogin acceptResponse(
            IdentityProvider provider, HttpServletRequest request, HttpServletResponse response) {
        OpenSaml5AuthenticationTokenConverter converter = new OpenSaml5AuthenticationTokenConverter(registrations);
        converter.setRequestMatcher(forRegistration(provider.getId()));
        converter.setAuthenticationRequestRepository(requests);
        Saml2AuthenticationToken token = converter.convert(request);
        if (token == null) {
            throw new IllegalArgumentException("No SAML response in the request.");
        }
        requests.removeAuthenticationRequest(request, response);
        Authentication authentication;
        try {
            authentication = authenticationProvider.authenticate(token);
        } catch (Saml2AuthenticationException ex) {
            throw new IllegalArgumentException("SAML response rejected: " + ex.getSaml2Error().getErrorCode()
                    + " " + ex.getSaml2Error().getDescription());
        }
        String samlResponse = request.getParameter("SAMLResponse");
        Boolean first = redis.opsForValue().setIfAbsent(
                "federation:saml:seen:" + sha256(samlResponse), "1", java.time.Duration.ofMinutes(10));
        if (Boolean.FALSE.equals(first)) {
            throw new IllegalArgumentException("SAML response rejected: replayed response.");
        }
        IdentityProviderService.FederationUserProfile profile = profile(provider, authentication);
        List<String> sessionIndexes = authentication.getCredentials() instanceof Saml2ResponseAssertionAccessor assertion
                ? assertion.getSessionIndexes()
                : authentication.getPrincipal() instanceof Saml2AuthenticatedPrincipal principal
                        ? principal.getSessionIndexes()
                        : List.of();
        return new SamlLogin(profile, profile.subject(), sessionIndexes == null ? List.of() : List.copyOf(sessionIndexes));
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public String metadata(IdentityProvider provider) {
        RelyingPartyRegistration registration = registrations.build(provider);
        return metadataResolver.resolve(registration);
    }

    private IdentityProviderService.FederationUserProfile profile(IdentityProvider provider, Authentication authentication) {
        String nameId;
        Map<String, List<Object>> attributes;
        if (authentication.getCredentials() instanceof Saml2ResponseAssertionAccessor assertion) {
            nameId = assertion.getNameId();
            attributes = assertion.getAttributes();
        } else if (authentication.getPrincipal() instanceof Saml2AuthenticatedPrincipal principal) {
            nameId = principal.getName();
            attributes = principal.getAttributes();
        } else {
            throw new IllegalArgumentException("SAML response did not identify a user.");
        }
        if (nameId == null || nameId.isBlank()) {
            throw new IllegalArgumentException("SAML response has no NameID.");
        }
        String email = email(provider, nameId, attributes);
        Object trust = provider.getConfig().get("trustEmail");
        boolean verified = email != null && !Boolean.FALSE.equals(trust) && !"false".equalsIgnoreCase(String.valueOf(trust));
        return new IdentityProviderService.FederationUserProfile(nameId.trim(), email, String.valueOf(attributes), verified,
                first(attributes, GIVEN_NAME), first(attributes, FAMILY_NAME), first(attributes, DISPLAY_NAME));
    }

    /** Attribute names identity providers commonly send the user's names in (LDAP/X.500 OIDs, Entra/ADFS claims). */
    private static final List<String> GIVEN_NAME = List.of("urn:oid:2.5.4.42", "givenName", "given_name", "firstName",
            "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname");
    private static final List<String> FAMILY_NAME = List.of("urn:oid:2.5.4.4", "sn", "surname", "family_name", "lastName",
            "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname");
    private static final List<String> DISPLAY_NAME = List.of("urn:oid:2.16.840.1.113730.3.1.241", "displayName", "name",
            "http://schemas.microsoft.com/identity/claims/displayname",
            "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name");

    private static String first(Map<String, List<Object>> attributes, List<String> names) {
        for (String name : names) {
            List<Object> values = attributes.get(name);
            if (values != null && !values.isEmpty() && values.get(0) != null && !String.valueOf(values.get(0)).isBlank()) {
                return String.valueOf(values.get(0)).trim();
            }
        }
        return null;
    }

    private static String email(IdentityProvider provider, String nameId, Map<String, List<Object>> attributes) {
        Object configured = provider.getConfig().get("emailAttribute");
        if (configured != null && !String.valueOf(configured).isBlank()) {
            return first(attributes, String.valueOf(configured).trim());
        }
        for (String name : EMAIL_ATTRIBUTES) {
            String value = first(attributes, name);
            if (value != null) {
                return value;
            }
        }
        return nameId.contains("@") ? nameId.trim().toLowerCase(Locale.ROOT) : null;
    }

    private static String first(Map<String, List<Object>> attributes, String name) {
        List<Object> values = attributes == null ? null : attributes.get(name);
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return null;
        }
        String value = String.valueOf(values.get(0)).trim();
        return value.isEmpty() ? null : value;
    }

    /** Hands Spring's resolver/converter the registration id without exposing Spring's default URLs. */
    private static RequestMatcher forRegistration(UUID providerId) {
        return new RequestMatcher() {
            @Override
            public boolean matches(HttpServletRequest request) {
                return true;
            }

            @Override
            public MatchResult matcher(HttpServletRequest request) {
                return MatchResult.match(Map.of("registrationId", providerId.toString()));
            }
        };
    }

    private static String autoPostForm(Saml2PostAuthenticationRequest post) {
        return """
                <!DOCTYPE html><html><head><meta charset="utf-8"><title>Signing in…</title></head>
                <body onload="document.forms[0].submit()">
                <form method="post" action="%s">
                <input type="hidden" name="SAMLRequest" value="%s">
                <input type="hidden" name="RelayState" value="%s">
                <noscript><button type="submit">Continue</button></noscript>
                </form></body></html>
                """
                .formatted(
                        HtmlUtils.htmlEscape(post.getAuthenticationRequestUri()),
                        HtmlUtils.htmlEscape(post.getSamlRequest()),
                        HtmlUtils.htmlEscape(post.getRelayState()));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
