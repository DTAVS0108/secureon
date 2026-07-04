package com.secureone.auth.mfa.passkey;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import java.net.URI;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebAuthnConfig {

    @Bean
    RelyingParty relyingParty(
            WebAuthnCredentialRepository credentials,
            @Value("${secureone.public-base-url:http://localhost:9000}") String publicBaseUrl) {
        String normalized = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        URI originUri = URI.create(normalized);
        String rpId = originUri.getHost();
        if (rpId == null || rpId.isBlank()) {
            rpId = "localhost";
        }
        RelyingPartyIdentity identity =
                RelyingPartyIdentity.builder().id(rpId).name("SecureOne").build();
        return RelyingParty.builder()
                .identity(identity)
                .credentialRepository(credentials)
                .origins(Set.of(normalized))
                .build();
    }
}
