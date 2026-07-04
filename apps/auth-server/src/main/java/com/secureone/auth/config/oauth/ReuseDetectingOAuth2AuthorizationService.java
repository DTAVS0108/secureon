package com.secureone.auth.config.oauth;

import com.secureone.auth.oauth.RefreshTokenReuseAuditService;
import com.secureone.auth.oauth.RefreshTokenReuseContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;

/** Delegates to JDBC storage and audits refresh-token reuse before authorization removal. */
@Service
@Primary
public class ReuseDetectingOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final RefreshTokenReuseAuditService reuseAudit;

    public ReuseDetectingOAuth2AuthorizationService(
            @Qualifier("jdbcOAuth2AuthorizationServiceDelegate")
                    JdbcOAuth2AuthorizationServiceDelegate delegate,
            RefreshTokenReuseAuditService reuseAudit) {
        this.delegate = delegate;
        this.reuseAudit = reuseAudit;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        String presented = RefreshTokenReuseContext.getPresentedRefreshToken();
        if (presented != null
                && authorization != null
                && authorization.getRefreshToken() != null
                && authorization.getRefreshToken().getToken() != null) {
            String stored = authorization.getRefreshToken().getToken().getTokenValue();
            if (!presented.equals(stored)) {
                reuseAudit.recordReuse(authorization, presented);
            }
        }
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }

    /** JDBC delegate; registered as a bean in {@link OAuth2AuthorizationServerPersistenceConfig}. */
    public static class JdbcOAuth2AuthorizationServiceDelegate
            extends org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService {
        public JdbcOAuth2AuthorizationServiceDelegate(
                org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
                        registeredClientRepository) {
            super(jdbcTemplate, registeredClientRepository);
        }
    }
}
