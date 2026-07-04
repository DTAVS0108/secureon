package com.secureone.auth.oauth;

/** Thread-local refresh token presented to {@code /oauth2/token} for reuse detection. */
public final class RefreshTokenReuseContext {

    private static final ThreadLocal<String> PRESENTED_REFRESH_TOKEN = new ThreadLocal<>();

    private RefreshTokenReuseContext() {}

    public static void setPresentedRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            PRESENTED_REFRESH_TOKEN.remove();
        } else {
            PRESENTED_REFRESH_TOKEN.set(token);
        }
    }

    public static String getPresentedRefreshToken() {
        return PRESENTED_REFRESH_TOKEN.get();
    }

    public static void clear() {
        PRESENTED_REFRESH_TOKEN.remove();
    }
}
