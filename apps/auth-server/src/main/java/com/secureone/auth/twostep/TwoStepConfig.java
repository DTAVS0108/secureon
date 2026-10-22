package com.secureone.auth.twostep;

import com.secureone.auth.mfa.MfaLoginService;
import com.secureone.auth.oauth.OAuthClientRepository;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TwoStepConfig {

    /** After Spring Session (Integer.MIN_VALUE + 50), before Spring Security (-100). */
    @Bean
    FilterRegistrationBean<SignInFreshnessFilter> signInFreshnessFilter(
            @Value("${secureone.session.max-lifetime:24h}") Duration maxLifetime,
            ObjectProvider<MfaLoginService> mfaLogin,
            ObjectProvider<OAuthClientRepository> clients) {
        FilterRegistrationBean<SignInFreshnessFilter> registration =
                new FilterRegistrationBean<>(new SignInFreshnessFilter(maxLifetime, mfaLogin, clients));
        registration.setOrder(-110);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
