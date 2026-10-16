package com.secureone.auth.federation;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;
import org.springframework.stereotype.Component;

/**
 * SAML 2.0 relying-party registrations built from tenant identity providers of type SAML. The
 * registration id is the provider's UUID (aliases are only unique per tenant/application). The asserting
 * party comes from the IdP's metadata URL when one is configured, otherwise from an entity id, SSO URL
 * and signing certificate entered by the operator.
 */
@Component
public class SamlRelyingParties implements RelyingPartyRegistrationRepository {

    static final String EMAIL_NAME_ID = "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress";
    private static final Duration METADATA_TTL = Duration.ofMinutes(10);

    private final IdentityProviderRepository providers;
    private final SamlSpCredentialService spCredential;
    private final Map<UUID, CachedMetadata> metadataCache = new ConcurrentHashMap<>();

    @Value("${secureone.public-base-url:http://localhost:9000}")
    private String publicBaseUrl;

    public SamlRelyingParties(IdentityProviderRepository providers, SamlSpCredentialService spCredential) {
        this.providers = providers;
        this.spCredential = spCredential;
    }

    private record CachedMetadata(String url, RelyingPartyRegistration registration, Instant fetchedAt) {}

    @Override
    public RelyingPartyRegistration findByRegistrationId(String registrationId) {
        UUID id;
        try {
            id = UUID.fromString(registrationId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return providers.findById(id)
                .filter(IdentityProvider::isEnabled)
                .filter(p -> FederationProviderType.SAML.name().equalsIgnoreCase(p.getType()))
                .map(this::build)
                .orElse(null);
    }

    /** Builds the registration, failing with a readable message when the configuration is incomplete. */
    public RelyingPartyRegistration build(IdentityProvider provider) {
        Map<String, Object> config = provider.getConfig();
        String metadataUrl = string(config.get("metadataUrl"));
        RelyingPartyRegistration.Builder builder;
        if (!metadataUrl.isBlank()) {
            builder = fromMetadata(provider.getId(), metadataUrl).mutate();
        } else {
            String idpEntityId = string(config.get("idpEntityId"));
            String ssoUrl = string(config.get("ssoUrl"));
            String certificate = string(config.get("idpCertificate"));
            if (idpEntityId.isBlank() || ssoUrl.isBlank() || certificate.isBlank()) {
                throw new IllegalArgumentException(
                        "SAML provider needs either a metadata URL, or the IdP entity ID, SSO URL and signing certificate.");
            }
            X509Certificate cert = parseCertificate(certificate);
            Saml2MessageBinding binding = "POST".equalsIgnoreCase(string(config.get("ssoBinding")))
                    ? Saml2MessageBinding.POST
                    : Saml2MessageBinding.REDIRECT;
            String sloUrl = string(config.get("sloUrl"));
            builder = RelyingPartyRegistration.withRegistrationId(provider.getId().toString())
                    .assertingPartyMetadata(party -> {
                        party.entityId(idpEntityId)
                                .singleSignOnServiceLocation(ssoUrl)
                                .singleSignOnServiceBinding(binding)
                                .wantAuthnRequestsSigned(false)
                                .verificationX509Credentials(c -> c.add(Saml2X509Credential.verification(cert)));
                        if (!sloUrl.isBlank()) {
                            party.singleLogoutServiceLocation(sloUrl)
                                    .singleLogoutServiceResponseLocation(sloUrl)
                                    .singleLogoutServiceBinding(Saml2MessageBinding.REDIRECT);
                        }
                    });
        }
        String nameIdFormat = string(config.get("nameIdFormat"));
        return builder
                .registrationId(provider.getId().toString())
                .entityId(spEntityId(provider))
                .assertionConsumerServiceLocation(acsUrl(provider.getId()))
                .assertionConsumerServiceBinding(Saml2MessageBinding.POST)
                .nameIdFormat(nameIdFormat.isBlank() ? EMAIL_NAME_ID : nameIdFormat)
                // AuthnRequests are signed (and the certificate is in our SP metadata); IdPs that do not
                // require it ignore the signature.
                .signingX509Credentials(c -> c.add(spCredential.signingCredential()))
                // Same key pair for encrypted assertions: the IdP encrypts to the certificate in our metadata.
                .decryptionX509Credentials(c -> c.add(spCredential.decryptionCredential()))
                .authnRequestsSigned(true)
                .singleLogoutServiceLocation(sloUrl(provider.getId()))
                .singleLogoutServiceResponseLocation(sloUrl(provider.getId()))
                .singleLogoutServiceBindings(b -> {
                    b.clear();
                    b.add(Saml2MessageBinding.REDIRECT);
                    b.add(Saml2MessageBinding.POST);
                })
                .build();
    }

    /** Validates a configuration before it is saved; the message is shown to the operator. */
    public void validate(IdentityProvider provider) {
        try {
            build(provider);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            metadataCache.remove(provider.getId());
            throw new IllegalArgumentException("Could not read the IdP metadata: " + rootMessage(ex));
        }
    }

    public void evict(UUID providerId) {
        metadataCache.remove(providerId);
    }

    public String spEntityId(IdentityProvider provider) {
        String configured = string(provider.getConfig().get("spEntityId"));
        return configured.isBlank()
                ? publicBaseUrl + "/api/v1/auth/federation/saml/" + provider.getId()
                : configured;
    }

    public String acsUrl(UUID providerId) {
        return publicBaseUrl + "/api/v1/auth/federation/saml/acs/" + providerId;
    }

    /** Single logout endpoint: receives the IdP's LogoutRequest and LogoutResponse. */
    public String sloUrl(UUID providerId) {
        return publicBaseUrl + "/api/v1/auth/federation/saml/slo/" + providerId;
    }

    /** Whether the IdP takes part in single logout (its metadata or the operator gave an SLO URL). */
    public boolean supportsLogout(RelyingPartyRegistration registration) {
        String location = registration.getAssertingPartyMetadata().getSingleLogoutServiceLocation();
        return location != null && !location.isBlank();
    }

    public Optional<IdentityProvider> provider(UUID providerId) {
        return providers.findById(providerId)
                .filter(p -> FederationProviderType.SAML.name().equalsIgnoreCase(p.getType()));
    }

    private RelyingPartyRegistration fromMetadata(UUID providerId, String url) {
        CachedMetadata cached = metadataCache.get(providerId);
        if (cached != null
                && cached.url().equals(url)
                && cached.fetchedAt().plus(METADATA_TTL).isAfter(Instant.now())) {
            return cached.registration();
        }
        RelyingPartyRegistration registration = RelyingPartyRegistrations.fromMetadataLocation(url)
                .registrationId(providerId.toString())
                .build();
        metadataCache.put(providerId, new CachedMetadata(url, registration, Instant.now()));
        return registration;
    }

    static X509Certificate parseCertificate(String pemOrBase64) {
        String body = pemOrBase64
                .replace("\\n", "\n")
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s+", "");
        try {
            byte[] der = Base64.getDecoder().decode(body);
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(der));
        } catch (IllegalArgumentException | CertificateException ex) {
            throw new IllegalArgumentException("The IdP signing certificate is not a valid X.509 certificate (PEM).");
        }
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return String.valueOf(t.getMessage());
    }

    private static String string(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
