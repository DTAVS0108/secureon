package com.secureone.auth.federation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.Base64;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.saml2.provider.service.authentication.AbstractSaml2AuthenticationRequest;
import org.springframework.security.saml2.provider.service.web.Saml2AuthenticationRequestRepository;
import org.springframework.stereotype.Component;

/**
 * Keeps the AuthnRequest we sent until the IdP answers, keyed by RelayState (our federation state token)
 * rather than the HTTP session: the IdP posts the response cross-site, where SameSite session cookies are
 * not sent, and the request id is needed to check the response's InResponseTo.
 */
@Component
public class SamlRequestStore implements Saml2AuthenticationRequestRepository<AbstractSaml2AuthenticationRequest> {

    private static final String PREFIX = "federation:saml:authn:";
    private static final Duration TTL = Duration.ofMinutes(10);
    // Only Spring's SAML request types and what they hold (strings, enums) may be deserialized.
    private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
            "org.springframework.security.saml2.provider.service.authentication.*;"
                    + "org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;"
                    + "java.lang.*;java.lang.Enum;!*");

    private final StringRedisTemplate redis;

    public SamlRequestStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public AbstractSaml2AuthenticationRequest loadAuthenticationRequest(HttpServletRequest request) {
        String key = key(request);
        return key == null ? null : decode(redis.opsForValue().get(key));
    }

    @Override
    public void saveAuthenticationRequest(
            AbstractSaml2AuthenticationRequest authenticationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (authenticationRequest == null || authenticationRequest.getRelayState() == null) {
            return;
        }
        redis.opsForValue().set(PREFIX + authenticationRequest.getRelayState(), encode(authenticationRequest), TTL);
    }

    @Override
    public AbstractSaml2AuthenticationRequest removeAuthenticationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        String key = key(request);
        if (key == null) {
            return null;
        }
        String raw = redis.opsForValue().getAndDelete(key);
        return decode(raw);
    }

    private static String key(HttpServletRequest request) {
        String relayState = request.getParameter("RelayState");
        return relayState == null || relayState.isBlank() ? null : PREFIX + relayState.trim();
    }

    private static String encode(AbstractSaml2AuthenticationRequest value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store the SAML request", ex);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static AbstractSaml2AuthenticationRequest decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(raw)))) {
            in.setObjectInputFilter(FILTER);
            return (AbstractSaml2AuthenticationRequest) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException | IllegalArgumentException ex) {
            return null;
        }
    }
}
