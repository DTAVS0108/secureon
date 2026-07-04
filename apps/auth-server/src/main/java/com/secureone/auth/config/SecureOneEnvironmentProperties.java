package com.secureone.auth.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "secureone")
public class SecureOneEnvironmentProperties {

    /** development | staging | production */
    private String environment = "development";

    private boolean swaggerEnabled = true;
    private boolean devAuthEnabled = true;
    private String corsAllowedOrigins = "";

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public boolean isSwaggerEnabled() {
        return swaggerEnabled;
    }

    public void setSwaggerEnabled(boolean swaggerEnabled) {
        this.swaggerEnabled = swaggerEnabled;
    }

    public boolean isDevAuthEnabled() {
        return devAuthEnabled;
    }

    public void setDevAuthEnabled(boolean devAuthEnabled) {
        this.devAuthEnabled = devAuthEnabled;
    }

    public List<String> getCorsAllowedOrigins() {
        if (corsAllowedOrigins == null || corsAllowedOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public void setCorsAllowedOrigins(String corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public boolean isProduction() {
        return "production".equalsIgnoreCase(environment);
    }

    public boolean isDevelopment() {
        return "development".equalsIgnoreCase(environment);
    }
}
