package com.secureone.auth.twostep;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The second-step options. The id is what policies, factors
 * ({@code mfa_factor.type}) and the API use.
 */
public enum TwoStepMethod {
    /** WebAuthn credential on the user's phone or computer (Windows Hello, Touch ID, Android). */
    PASSKEY("passkey", "Passkey", Strength.PHISHING_RESISTANT),
    /** WebAuthn credential on a removable key (YubiKey). Stored as a passkey factor with kind = security_key. */
    SECURITY_KEY("security_key", "Security key", Strength.PHISHING_RESISTANT),
    /** Authenticator app code (RFC 6238). */
    TOTP("totp", "Authenticator app", Strength.STRONG),
    /** Code sent by text message to a verified phone. Restricted under NIST SP 800-63B (SIM swap). */
    SMS("sms", "Text message", Strength.BASIC),
    /** Code sent to the account's verified email. NIST: not a real second factor; opt-in only. */
    EMAIL("email", "Email code", Strength.BASIC);

    /** How much a method proves; a policy's minimum strength decides which methods satisfy "required". */
    public enum Strength {
        BASIC("any"),
        STRONG("strong"),
        PHISHING_RESISTANT("phishing_resistant");

        private final String id;

        Strength(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Strength fromId(String id) {
            return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst().orElse(STRONG);
        }
    }

    public static final List<String> IDS = Arrays.stream(values()).map(TwoStepMethod::id).toList();

    private final String id;
    private final String label;
    private final Strength strength;

    TwoStepMethod(String id, String label, Strength strength) {
        this.id = id;
        this.label = label;
        this.strength = strength;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public Strength strength() {
        return strength;
    }

    public boolean meets(Strength minimum) {
        return strength.ordinal() >= minimum.ordinal();
    }

    public boolean isWebAuthn() {
        return this == PASSKEY || this == SECURITY_KEY;
    }

    /** A code sent to the same kind of address a code or link first step already used. */
    public boolean isSentCode() {
        return this == SMS || this == EMAIL;
    }

    public static Optional<TwoStepMethod> fromId(String id) {
        return Arrays.stream(values()).filter(m -> m.id.equals(id)).findFirst();
    }
}
