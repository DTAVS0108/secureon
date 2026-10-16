package com.secureone.auth.application;

import com.secureone.auth.oauth.OAuthClient;
import com.secureone.auth.oauth.OAuthClientRepository;
import com.secureone.auth.tenant.RlsScope;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Resolves integrated applications from OAuth client identifiers. */
@Service
@Transactional(readOnly = true)
public class ApplicationOAuthClientResolver {

    private final OAuthClientRepository oauthClients;
    private final ApplicationRepository applications;

    public ApplicationOAuthClientResolver(
            OAuthClientRepository oauthClients, ApplicationRepository applications) {
        this.oauthClients = oauthClients;
        this.applications = applications;
    }

    // Outside any transaction, so each lookup opens its connection inside the platform scope below
    // (a connection taken by an enclosing transaction would keep that transaction's tenant scope).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<Application> findActiveByOAuthClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        // Runs before any tenant is known (anonymous sign-in and discovery requests), so it reads across
        // tenants like the OAuth client lookups; it only maps a known client_id to its active application.
        return RlsScope.asPlatform(() -> oauthClients
                .findByClientId(clientId.trim())
                .filter(this::isActiveClient)
                .flatMap(client -> applications.findById(client.getApplicationId()))
                .filter(this::isActiveApplication));
    }

    private boolean isActiveClient(OAuthClient client) {
        return "ACTIVE".equalsIgnoreCase(client.getStatus());
    }

    private boolean isActiveApplication(Application app) {
        return "ACTIVE".equalsIgnoreCase(app.getStatus());
    }
}
