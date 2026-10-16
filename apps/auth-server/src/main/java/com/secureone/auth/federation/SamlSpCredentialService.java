package com.secureone.auth.federation;

import com.secureone.auth.platform.PlatformSettingsService;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.stereotype.Service;

/**
 * The service provider's SAML signing credential: AuthnRequests are signed with it and its certificate
 * is published in the SP metadata, so IdPs that require signed requests (many enterprise IdPs and
 * setups) accept them. Taken from SECUREONE_SAML_SP_KEY_PEM / SECUREONE_SAML_SP_CERT_PEM when set,
 * otherwise generated once (RSA-2048, self-signed, 10 years, as SAML SPs normally use) and kept in the
 * platform settings, the private key encrypted with {@link com.secureone.auth.notify.SecretProtector}.
 */
@Service
public class SamlSpCredentialService {

    private static final String SETTING_KEY = "saml_sp_credential";

    private final PlatformSettingsService platformSettings;
    private final com.secureone.auth.notify.SecretProtector secrets;
    private final com.secureone.auth.platform.ClusterInitLock initLock;
    private final String envKeyPem;
    private final String envCertPem;
    private volatile Saml2X509Credential cached;

    public SamlSpCredentialService(
            PlatformSettingsService platformSettings,
            com.secureone.auth.notify.SecretProtector secrets,
            com.secureone.auth.platform.ClusterInitLock initLock,
            @Value("${SECUREONE_SAML_SP_KEY_PEM:}") String envKeyPem,
            @Value("${SECUREONE_SAML_SP_CERT_PEM:}") String envCertPem) {
        this.platformSettings = platformSettings;
        this.secrets = secrets;
        this.initLock = initLock;
        this.envKeyPem = envKeyPem == null ? "" : envKeyPem.trim();
        this.envCertPem = envCertPem == null ? "" : envCertPem.trim();
    }

    public Saml2X509Credential signingCredential() {
        Saml2X509Credential credential = cached;
        if (credential != null) {
            return credential;
        }
        synchronized (this) {
            if (cached == null) {
                cached = resolve();
            }
            return cached;
        }
    }

    /** The same key pair, for decrypting assertions the IdP encrypted to our certificate. */
    public Saml2X509Credential decryptionCredential() {
        Saml2X509Credential signing = signingCredential();
        return Saml2X509Credential.decryption(signing.getPrivateKey(), signing.getCertificate());
    }

    private Saml2X509Credential resolve() {
        if (!envKeyPem.isBlank() && !envCertPem.isBlank()) {
            return Saml2X509Credential.signing(parseKey(envKeyPem), SamlRelyingParties.parseCertificate(envCertPem));
        }
        // Several replicas may start at once: one creates the key, the others read it.
        return initLock.readOrCreate(SETTING_KEY, this::readStored, this::generateAndStore);
    }

    private Saml2X509Credential readStored() {
        Map<String, Object> stored = platformSettings.get(SETTING_KEY);
        String keyPem = secrets.decrypt(string(stored.get("privateKeyEncrypted")));
        String certPem = string(stored.get("certificatePem"));
        String legacyPlaintext = string(stored.get("privateKeyPem"));
        if (keyPem.isBlank() && !legacyPlaintext.isBlank() && !certPem.isBlank()) {
            // Stored before keys were encrypted at rest: keep the same key (IdPs trust its certificate).
            keyPem = legacyPlaintext;
            Map<String, Object> upgraded = new LinkedHashMap<>(stored);
            upgraded.remove("privateKeyPem");
            upgraded.put("privateKeyEncrypted", secrets.encrypt(keyPem));
            platformSettings.saveMap(SETTING_KEY, upgraded);
        }
        if (!keyPem.isBlank() && !certPem.isBlank()) {
            return Saml2X509Credential.signing(parseKey(keyPem), SamlRelyingParties.parseCertificate(certPem));
        }
        return null;
    }

    private Saml2X509Credential generateAndStore() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            Instant now = Instant.now();
            X500Name subject = new X500Name("CN=SecureOne SAML SP");
            X509Certificate cert = new JcaX509CertificateConverter().getCertificate(
                    new JcaX509v3CertificateBuilder(
                                    subject,
                                    new BigInteger(64, new SecureRandom()),
                                    Date.from(now.minus(Duration.ofDays(1))),
                                    Date.from(now.plus(Duration.ofDays(3650))),
                                    subject,
                                    pair.getPublic())
                            .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
            Map<String, Object> payload = new LinkedHashMap<>();
            // The private key is stored encrypted (SecretProtector); the certificate is public.
            payload.put("privateKeyEncrypted", secrets.encrypt(pem(pair.getPrivate().getEncoded(), "PRIVATE KEY")));
            payload.put("certificatePem", pem(cert.getEncoded(), "CERTIFICATE"));
            payload.put("createdAt", now.toString());
            platformSettings.saveMap(SETTING_KEY, payload);
            return Saml2X509Credential.signing(pair.getPrivate(), cert);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create the SAML SP signing credential", ex);
        }
    }

    private static PrivateKey parseKey(String pem) {
        String body = pem.replace("\\n", "\n")
                .replaceAll("-----(BEGIN|END) (RSA )?PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (Exception ex) {
            throw new IllegalStateException("SECUREONE_SAML_SP_KEY_PEM is not a PKCS#8 RSA private key", ex);
        }
    }

    private static String pem(byte[] der, String label) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + label + "-----";
    }

    private static String string(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }
}
