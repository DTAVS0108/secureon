package com.secureone.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "secureone.rate-limit")
public class SecureOneRateLimitProperties {

    private boolean enabled = true;
    private int loginPerMinute = 20;
    private int tokenPerMinute = 60;
    private int forgotPasswordPerMinute = 5;
    private int otpRequestPerMinute = 10;
    private int adminLoginPerMinute = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getLoginPerMinute() {
        return loginPerMinute;
    }

    public void setLoginPerMinute(int loginPerMinute) {
        this.loginPerMinute = loginPerMinute;
    }

    public int getTokenPerMinute() {
        return tokenPerMinute;
    }

    public void setTokenPerMinute(int tokenPerMinute) {
        this.tokenPerMinute = tokenPerMinute;
    }

    public int getForgotPasswordPerMinute() {
        return forgotPasswordPerMinute;
    }

    public void setForgotPasswordPerMinute(int forgotPasswordPerMinute) {
        this.forgotPasswordPerMinute = forgotPasswordPerMinute;
    }

    public int getOtpRequestPerMinute() {
        return otpRequestPerMinute;
    }

    public void setOtpRequestPerMinute(int otpRequestPerMinute) {
        this.otpRequestPerMinute = otpRequestPerMinute;
    }

    public int getAdminLoginPerMinute() {
        return adminLoginPerMinute;
    }

    public void setAdminLoginPerMinute(int adminLoginPerMinute) {
        this.adminLoginPerMinute = adminLoginPerMinute;
    }
}
