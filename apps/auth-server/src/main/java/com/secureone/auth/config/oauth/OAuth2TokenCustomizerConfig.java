package com.secureone.auth.config.oauth;

import com.secureone.auth.authn.AuthContextAttributes;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

@Configuration
public class OAuth2TokenCustomizerConfig {

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> jwtEncodingContextCustomizer() {
        return context -> {
            List<String> amr = AuthContextAttributes.amrFromCurrentRequest();
            String acr = AuthContextAttributes.acrFromCurrentRequest();
            if (!amr.isEmpty()) {
                context.getClaims().claim("amr", amr);
            }
            if (acr != null && !acr.isBlank() && !"0".equals(acr)) {
                context.getClaims().claim("acr", acr);
            }
        };
    }
}
