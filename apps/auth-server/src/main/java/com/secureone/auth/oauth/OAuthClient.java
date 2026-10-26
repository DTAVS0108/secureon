package com.secureone.auth.oauth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** OAuth 2.1 / OIDC client credentials registered under an application product. */
@Entity
@Table(name = "oauth_client")
@Getter
@Setter
public class OAuthClient {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "client_secret")
    private String clientSecret;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "type", nullable = false)
    private String type = "web";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "redirect_uris", nullable = false, columnDefinition = "jsonb")
    private List<String> redirectUris = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "post_logout_redirect_uris", nullable = false, columnDefinition = "jsonb")
    private List<String> postLogoutRedirectUris = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "grant_types", nullable = false, columnDefinition = "jsonb")
    private List<String> grantTypes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scopes", nullable = false, columnDefinition = "jsonb")
    private List<String> scopes = new ArrayList<>();

    @Column(name = "token_endpoint_auth_method", nullable = false)
    private String tokenEndpointAuthMethod = "client_secret_basic";

    @Column(name = "require_pkce", nullable = false)
    private boolean requirePkce = true;

    /** Token "sub": {@link #SUBJECT_USER_ID} (account UUID, the default for new clients) or {@link #SUBJECT_LEGACY}. */
    @Column(name = "subject_type", nullable = false)
    private String subjectType = SUBJECT_USER_ID;

    public static final String SUBJECT_USER_ID = "user_id";
    public static final String SUBJECT_LEGACY = "legacy";

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    /** Framework chosen in "Connect an app" (console display of setup code only). */
    @Column(name = "setup_framework")
    private String setupFramework;

    /** OIDC Back-Channel Logout: where SecureOne POSTs a logout_token when the user signs out (null: none). */
    @Column(name = "backchannel_logout_uri")
    private String backchannelLogoutUri;

    /** Third-party client: users approve the access it asks for (consent screen). */
    @Column(name = "consent_required", nullable = false)
    private boolean consentRequired;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getSetupFramework() {
        return setupFramework;
    }

    public boolean isConsentRequired() {
        return consentRequired;
    }

    public void setConsentRequired(boolean consentRequired) {
        this.consentRequired = consentRequired;
    }

    public String getBackchannelLogoutUri() {
        return backchannelLogoutUri;
    }

    public void setBackchannelLogoutUri(String backchannelLogoutUri) {
        this.backchannelLogoutUri = backchannelLogoutUri;
    }

    public void setSetupFramework(String setupFramework) {
        this.setupFramework = setupFramework;
    }
}
