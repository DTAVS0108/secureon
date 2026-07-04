package com.secureone.auth.mfa.passkey;

import com.secureone.auth.mfa.MfaFactorRepository;
import com.secureone.auth.tenant.TenantRepository;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class WebAuthnCredentialRepository implements CredentialRepository {

    static final String FACTOR_TYPE = "passkey";
    static final String META_CREDENTIAL_ID = "credentialId";
    static final String META_USER_HANDLE = "userHandle";
    static final String META_PUBLIC_KEY = "publicKeyCose";
    static final String META_SIGNATURE_COUNT = "signatureCount";
    static final String USER_HANDLE_ATTR = "webauthnUserHandle";

    private final MfaFactorRepository factors;
    private final UserAccountRepository users;
    private final TenantRepository tenants;

    public WebAuthnCredentialRepository(
            MfaFactorRepository factors, UserAccountRepository users, TenantRepository tenants) {
        this.factors = factors;
        this.users = users;
        this.tenants = tenants;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return resolveUser(username)
                .map(user -> factors.findByUserIdAndType(user.getId(), FACTOR_TYPE).stream()
                        .filter(com.secureone.auth.mfa.MfaFactor::isVerified)
                        .map(this::toDescriptor)
                        .flatMap(Optional::stream)
                        .collect(java.util.stream.Collectors.toSet()))
                .orElseGet(Set::of);
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return resolveUser(username).map(WebAuthnUserHandleService::readHandle);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return findUserByHandle(userHandle).flatMap(this::usernameForUser);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return lookupAll(credentialId).stream()
                .filter(c -> c.getUserHandle().equals(userHandle))
                .findFirst();
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        String encoded = encode(credentialId);
        return factors.findPasskeyByCredentialId(encoded)
                .flatMap(this::toRegisteredCredential)
                .map(Set::of)
                .orElse(Set.of());
    }

    @Transactional
    public com.secureone.auth.mfa.MfaFactor saveCredential(
            UUID userId, String label, RegisteredCredential credential, Map<String, Object> extraMetadata) {
        com.secureone.auth.mfa.MfaFactor factor = new com.secureone.auth.mfa.MfaFactor();
        factor.setUserId(userId);
        factor.setType(FACTOR_TYPE);
        factor.setLabel(label != null && !label.isBlank() ? label.trim() : "Passkey");
        factor.setVerified(true);
        factor.setMetadata(metadataFor(credential, extraMetadata));
        return factors.save(factor);
    }

    @Transactional
    public void updateSignatureCount(com.secureone.auth.mfa.MfaFactor factor, long signatureCount) {
        Map<String, Object> metadata = factor.getMetadata();
        metadata.put(META_SIGNATURE_COUNT, signatureCount);
        factor.setMetadata(metadata);
        factors.save(factor);
    }

    @Transactional
    public ByteArray ensureUserHandle(UserAccount user) {
        ByteArray existing = WebAuthnUserHandleService.readHandle(user);
        if (existing != null) {
            return existing;
        }
        ByteArray handle = new ByteArray(new SecureRandom().generateSeed(32));
        user.getAttributes().put(USER_HANDLE_ATTR, encode(handle));
        users.save(user);
        return handle;
    }

    Optional<UserAccount> resolveUser(String username) {
        int sep = username.indexOf(':');
        if (sep <= 0) {
            return Optional.empty();
        }
        String tenantSlug = username.substring(0, sep).trim().toLowerCase(Locale.ROOT);
        String email = username.substring(sep + 1).trim().toLowerCase(Locale.ROOT);
        return tenants.findBySlug(tenantSlug).flatMap(tenant -> users.findByTenantIdAndEmail(tenant.getId(), email));
    }

    Optional<String> usernameForUser(UserAccount user) {
        return tenants.findById(user.getTenantId()).map(tenant -> tenant.getSlug() + ":" + user.getEmail());
    }

    private Optional<UserAccount> findUserByHandle(ByteArray userHandle) {
        List<UserAccount> matches = users.findByWebAuthnUserHandle(encode(userHandle));
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.getFirst());
    }

    private Optional<RegisteredCredential> toRegisteredCredential(com.secureone.auth.mfa.MfaFactor factor) {
        Map<String, Object> metadata = factor.getMetadata();
        if (metadata == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(RegisteredCredential.builder()
                    .credentialId(decode(string(metadata.get(META_CREDENTIAL_ID))))
                    .userHandle(decode(string(metadata.get(META_USER_HANDLE))))
                    .publicKeyCose(decode(string(metadata.get(META_PUBLIC_KEY))))
                    .signatureCount(longValue(metadata.get(META_SIGNATURE_COUNT)))
                    .build());
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<PublicKeyCredentialDescriptor> toDescriptor(com.secureone.auth.mfa.MfaFactor factor) {
        return toRegisteredCredential(factor).map(c -> PublicKeyCredentialDescriptor.builder()
                .id(c.getCredentialId())
                .build());
    }

    private static Map<String, Object> metadataFor(RegisteredCredential credential, Map<String, Object> extra) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put(META_CREDENTIAL_ID, encode(credential.getCredentialId()));
        metadata.put(META_USER_HANDLE, encode(credential.getUserHandle()));
        metadata.put(META_PUBLIC_KEY, encode(credential.getPublicKeyCose()));
        metadata.put(META_SIGNATURE_COUNT, credential.getSignatureCount());
        if (extra != null) {
            metadata.putAll(extra);
        }
        return metadata;
    }

    static String encode(ByteArray value) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes());
    }

    static ByteArray decode(String encoded) {
        return new ByteArray(java.util.Base64.getUrlDecoder().decode(encoded));
    }

    private static String string(Object raw) {
        return raw != null ? raw.toString() : "";
    }

    private static long longValue(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(string(raw));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
