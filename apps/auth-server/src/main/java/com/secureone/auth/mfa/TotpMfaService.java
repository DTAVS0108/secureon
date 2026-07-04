package com.secureone.auth.mfa;

import com.secureone.auth.notify.SecretProtector;
import com.secureone.auth.user.UserAccount;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TotpMfaService {

    public record EnrollResponse(UUID factorId, String secret, String otpauthUri, String qrCodeDataUrl) {}

    public record ConfirmResponse(UUID factorId, List<String> recoveryCodes) {}

    private final MfaFactorRepository factors;
    private final MfaRecoveryCodeService recoveryCodes;
    private final MfaPolicyService policy;
    private final SecretProtector secrets;
    private final String issuer;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeVerifier codeVerifier =
            new DefaultCodeVerifier(new DefaultCodeGenerator(HashingAlgorithm.SHA1), timeProvider);

    public TotpMfaService(
            MfaFactorRepository factors,
            MfaRecoveryCodeService recoveryCodes,
            MfaPolicyService policy,
            SecretProtector secrets,
            @Value("${secureone.public-base-url:http://localhost:9000}") String publicBaseUrl) {
        this.factors = factors;
        this.recoveryCodes = recoveryCodes;
        this.policy = policy;
        this.secrets = secrets;
        this.issuer = issuerName(publicBaseUrl);
    }

    public EnrollResponse enroll(UserAccount user, String label) {
        if (!policy.totpAvailable()) {
            throw new IllegalStateException("TOTP MFA is not enabled on this platform.");
        }
        factors.findByUserIdAndType(user.getId(), "totp").stream()
                .filter(MfaFactor::isVerified)
                .findFirst()
                .ifPresent(f -> {
                    throw new IllegalStateException("A verified authenticator is already enrolled.");
                });
        factors.findByUserIdAndType(user.getId(), "totp").forEach(factors::delete);

        String secret = secretGenerator.generate();
        MfaFactor factor = new MfaFactor();
        factor.setUserId(user.getId());
        factor.setType("totp");
        factor.setLabel(label != null && !label.isBlank() ? label.trim() : "Authenticator app");
        factor.setVerified(false);
        factor.setSecretEncrypted(encryptSecret(secret));
        factors.save(factor);

        QrData qrData = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer(issuer)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        String otpauthUri = qrData.getUri();
        String qrCodeDataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(generateQr(qrData));

        return new EnrollResponse(factor.getId(), secret, otpauthUri, qrCodeDataUrl);
    }

    public ConfirmResponse confirm(UserAccount user, UUID factorId, String code) {
        MfaFactor factor = requirePendingFactor(user.getId(), factorId);
        String secret = decryptSecret(factor.getSecretEncrypted());
        if (!codeVerifier.isValidCode(secret, normalizeCode(code))) {
            throw new IllegalArgumentException("Invalid authenticator code.");
        }
        factor.setVerified(true);
        factors.save(factor);
        List<String> codes = recoveryCodes.issueForUser(user.getId(), policy.recoveryCodesPerEnrollment());
        return new ConfirmResponse(factor.getId(), codes);
    }

    public void delete(UserAccount user, UUID factorId) {
        MfaFactor factor = factors
                .findByIdAndUserId(factorId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("MFA factor not found."));
        if (!"totp".equalsIgnoreCase(factor.getType())) {
            throw new IllegalArgumentException("Only TOTP factors can be removed here.");
        }
        factors.delete(factor);
        recoveryCodes.revokeForUser(user.getId());
    }

    public boolean verifyForUser(UUID userId, String code) {
        MfaFactor factor = factors
                .findFirstByUserIdAndTypeAndVerifiedTrueOrderByCreatedAtAsc(userId, "totp")
                .orElseThrow(() -> new IllegalArgumentException("No verified authenticator enrolled."));
        String secret = decryptSecret(factor.getSecretEncrypted());
        boolean valid = codeVerifier.isValidCode(secret, normalizeCode(code));
        if (valid) {
            factor.setLastUsedAt(java.time.Instant.now());
            factors.save(factor);
        }
        return valid;
    }

    private MfaFactor requirePendingFactor(UUID userId, UUID factorId) {
        MfaFactor factor = factors
                .findByIdAndUserId(factorId, userId)
                .orElseThrow(() -> new IllegalArgumentException("MFA factor not found."));
        if (!"totp".equalsIgnoreCase(factor.getType())) {
            throw new IllegalArgumentException("Invalid MFA factor type.");
        }
        if (factor.isVerified()) {
            throw new IllegalStateException("Authenticator is already confirmed.");
        }
        return factor;
    }

    private byte[] encryptSecret(String secret) {
        return secrets.encrypt(secret).getBytes(StandardCharsets.UTF_8);
    }

    private String decryptSecret(byte[] encrypted) {
        if (encrypted == null || encrypted.length == 0) {
            throw new IllegalStateException("Missing TOTP secret.");
        }
        return secrets.decrypt(new String(encrypted, StandardCharsets.UTF_8));
    }

    private static byte[] generateQr(QrData data) {
        try {
            QrGenerator generator = new ZxingPngQrGenerator();
            return generator.generate(data);
        } catch (QrGenerationException ex) {
            throw new IllegalStateException("Failed to generate QR code", ex);
        }
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.replaceAll("\\s", "").trim();
    }

    private static String issuerName(String publicBaseUrl) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return "SecureOne";
        }
        String trimmed = publicBaseUrl.replace("https://", "").replace("http://", "");
        int slash = trimmed.indexOf('/');
        return slash > 0 ? trimmed.substring(0, slash) : trimmed;
    }
}
