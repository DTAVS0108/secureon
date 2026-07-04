package com.secureone.auth.mfa.passkey;

import com.secureone.auth.user.UserAccount;
import com.yubico.webauthn.data.ByteArray;
import java.util.Map;

final class WebAuthnUserHandleService {

    private WebAuthnUserHandleService() {}

    static ByteArray readHandle(UserAccount user) {
        if (user.getAttributes() == null) {
            return null;
        }
        Object raw = user.getAttributes().get(WebAuthnCredentialRepository.USER_HANDLE_ATTR);
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        return WebAuthnCredentialRepository.decode(raw.toString());
    }
}
