package com.secureone.auth.saml;

import com.secureone.auth.tenant.RlsScope;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Each tenant IdP's signing key: created on first use (RSA-2048, self-signed certificate valid 10 years, as SAML
 * IdPs normally publish), the private key stored encrypted. Service providers trust the certificate from the
 * IdP metadata.
 */
@Service
public class SamlIdpCredentialService {

    public record Credential(PrivateKey privateKey, X509Certificate certificate) {}

    private final JdbcTemplate jdbc;
    private final com.secureone.auth.notify.SecretProtector secrets;
    private final Map<UUID, Credential> cache = new ConcurrentHashMap<>();

    public SamlIdpCredentialService(JdbcTemplate jdbc, com.secureone.auth.notify.SecretProtector secrets) {
        this.jdbc = jdbc;
        this.secrets = secrets;
    }

    public Credential credential(UUID tenantId) {
        return cache.computeIfAbsent(tenantId, this::loadOrCreate);
    }

    private Credential loadOrCreate(UUID tenantId) {
        Credential stored = load(tenantId);
        if (stored != null) {
            return stored;
        }
        Credential created = generate(tenantId);
        // Two nodes may create one at once: the first stored wins and both use it.
        RlsScope.runAsPlatform(() -> jdbc.update(
                "INSERT INTO platform.saml_idp_credential (tenant_id, private_key_encrypted, certificate_pem) VALUES (?, ?, ?) "
                        + "ON CONFLICT (tenant_id) DO NOTHING",
                tenantId, secrets.encrypt(pem(created.privateKey().getEncoded(), "PRIVATE KEY")),
                pemOf(created.certificate())));
        return load(tenantId);
    }

    private Credential load(UUID tenantId) {
        return RlsScope.asPlatform(() -> jdbc.query(
                "SELECT private_key_encrypted, certificate_pem FROM platform.saml_idp_credential WHERE tenant_id = ?",
                (rs, i) -> new Credential(
                        parseKey(secrets.decrypt(rs.getString(1))), parseCertificate(rs.getString(2))),
                tenantId).stream().findFirst().orElse(null));
    }

    private static Credential generate(UUID tenantId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            Instant now = Instant.now();
            X500Name subject = new X500Name("CN=SecureOne SAML IdP " + tenantId);
            X509Certificate cert = new JcaX509CertificateConverter().getCertificate(
                    new JcaX509v3CertificateBuilder(
                                    subject,
                                    new BigInteger(64, new SecureRandom()),
                                    Date.from(now.minus(Duration.ofDays(1))),
                                    Date.from(now.plus(Duration.ofDays(3650))),
                                    subject,
                                    pair.getPublic())
                            .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
            return new Credential(pair.getPrivate(), cert);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create the SAML IdP signing key", ex);
        }
    }

    /** The certificate's base64 DER (as in metadata). */
    public static String base64(X509Certificate certificate) {
        try {
            return Base64.getEncoder().encodeToString(certificate.getEncoded());
        } catch (java.security.cert.CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String pemOf(X509Certificate certificate) {
        try {
            return pem(certificate.getEncoded(), "CERTIFICATE");
        } catch (java.security.cert.CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A PEM or bare base64 X.509 certificate. */
    public static X509Certificate parseCertificate(String pemOrBase64) {
        String body = pemOrBase64.replace("\\n", "\n")
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s+", "");
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(body)));
        } catch (Exception e) {
            throw new IllegalArgumentException("The certificate is not a valid X.509 certificate (PEM).");
        }
    }

    private static PrivateKey parseKey(String pem) {
        String body = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (Exception ex) {
            throw new IllegalStateException("The stored SAML IdP key cannot be read", ex);
        }
    }

    private static String pem(byte[] der, String label) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + label + "-----";
    }
}
