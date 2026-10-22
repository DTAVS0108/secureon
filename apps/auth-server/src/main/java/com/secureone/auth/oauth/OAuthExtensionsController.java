package com.secureone.auth.oauth;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OAuth extension endpoints not provided by Spring Authorization Server. PAR (/oauth2/par) is built in. */
@Tag(name = "OAuth 2.1 / OIDC — extensions", description = "Pushed authorization requests, token exchange and other OAuth extensions")
@RestController
@RequestMapping("/oauth2")
public class OAuthExtensionsController {

    @PostMapping("/backchannel-logout")
    public org.springframework.http.ResponseEntity<Void> backchannelLogout(
            @RequestBody(required = false) Map<String, Object> body) {
        return org.springframework.http.ResponseEntity.ok().build();
    }
}
