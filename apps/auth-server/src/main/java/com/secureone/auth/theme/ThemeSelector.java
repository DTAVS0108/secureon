package com.secureone.auth.theme;

import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationOAuthClientResolver;
import com.secureone.auth.application.ApplicationSettingsService;
import com.secureone.auth.tenant.RlsScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Which theme a hosted page is shown in. In order: {@code ?theme=} (the console's preview; only installed themes),
 * then the application's choice (Application → Settings → Appearance → Sign-in page theme), then SECUREONE_THEME,
 * then {@code base}. The application comes from {@code ?applicationId=}, the {@code client_id} of the page (consent)
 * or of the sign-in request in {@code ?continue=}, or the sign-in request waiting in the session.
 */
@Component
public class ThemeSelector {

    private static final Logger log = LoggerFactory.getLogger(ThemeSelector.class);
    /** Where Spring Security keeps the request that led to the sign-in page (HttpSessionRequestCache). */
    static final String SAVED_REQUEST = "SPRING_SECURITY_SAVED_REQUEST";

    private final ThemeRegistry themes;
    private final ObjectProvider<ApplicationOAuthClientResolver> clients;
    private final ObjectProvider<ApplicationSettingsService> settings;

    public ThemeSelector(
            ThemeRegistry themes,
            ObjectProvider<ApplicationOAuthClientResolver> clients,
            ObjectProvider<ApplicationSettingsService> settings) {
        this.themes = themes;
        this.clients = clients;
        this.settings = settings;
    }

    public String select(HttpServletRequest request) {
        String preview = request.getParameter("theme");
        if (preview != null && themes.exists(preview.trim())) {
            return preview.trim();
        }
        return applicationOf(request).map(this::themeOf).orElseGet(themes::defaultTheme);
    }

    /** The theme an application uses: its own choice when installed, otherwise the server default. */
    public String themeOf(UUID applicationId) {
        try {
            ApplicationSettingsService service = settings.getIfAvailable();
            if (service == null) {
                return themes.defaultTheme();
            }
            Object chosen = RlsScope.asPlatform(() -> service.resolveAppearance(applicationId).get("theme"));
            if (chosen instanceof String name && !name.isBlank()) {
                if (themes.exists(name)) {
                    return name;
                }
                log.debug("Application {} uses sign-in theme '{}', which is not installed: using the default", applicationId, name);
            }
        } catch (RuntimeException e) {
            log.debug("No sign-in theme for application {}: {}", applicationId, e.getMessage());
        }
        return themes.defaultTheme();
    }

    Optional<UUID> applicationOf(HttpServletRequest request) {
        Optional<UUID> explicit = uuid(request.getParameter("applicationId"));
        if (explicit.isPresent()) {
            return explicit;
        }
        String clientId = request.getParameter("client_id");
        if (blank(clientId)) {
            clientId = clientIdOf(request.getParameter("continue"));
        }
        if (blank(clientId)) {
            clientId = savedClientId(request);
        }
        if (blank(clientId)) {
            return Optional.empty();
        }
        ApplicationOAuthClientResolver resolver = clients.getIfAvailable();
        if (resolver == null) {
            return Optional.empty();
        }
        try {
            return resolver.findActiveByOAuthClientId(clientId).map(Application::getId);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String savedClientId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        try {
            Object saved = session.getAttribute(SAVED_REQUEST);
            if (saved instanceof SavedRequest s) {
                String[] values = s.getParameterValues("client_id");
                if (values != null && values.length > 0) {
                    return values[0];
                }
                return clientIdOf(s.getRedirectUrl());
            }
        } catch (IllegalStateException e) {
            // Session ended meanwhile.
        }
        return null;
    }

    static String clientIdOf(String url) {
        if (blank(url)) {
            return null;
        }
        try {
            return UriComponentsBuilder.fromUri(URI.create(url.trim())).build().getQueryParams().getFirst("client_id");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Optional<UUID> uuid(String raw) {
        if (blank(raw)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
