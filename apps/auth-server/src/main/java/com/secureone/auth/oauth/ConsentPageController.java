package com.secureone.auth.oauth;

import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.theme.ThemePages;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The OAuth consent screen for third-party clients (OAuth clients with "Ask users for consent" on): which
 * application is asking and what it will be able to read; the user allows or cancels. Approved access is
 * remembered per application and can be withdrawn under Account security › Connected apps.
 */
@Tag(name = "OAuth 2.1 / OIDC — consent", description = "The consent screen shown to users for third-party clients")
@RestController
public class ConsentPageController {

    public static final String PATH = "/oauth2/consent";

    static final Map<String, String> SCOPE_TEXT = Map.of(
            "openid", "Know who you are",
            "profile", "Your name and username",
            "email", "Your email address",
            "phone", "Your phone number",
            "address", "Your address",
            "offline_access", "Stay connected when you are not using it");

    private final RegisteredClientRepository clients;
    private final OAuth2AuthorizationConsentService consents;
    private final ThemePages pages;

    public ConsentPageController(
            RegisteredClientRepository clients, OAuth2AuthorizationConsentService consents, ThemePages pages) {
        this.clients = clients;
        this.consents = consents;
        this.pages = pages;
    }

    @GetMapping(value = PATH, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String consent(
            Principal principal,
            @RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
            @RequestParam(OAuth2ParameterNames.SCOPE) String scope,
            @RequestParam(OAuth2ParameterNames.STATE) String state,
            HttpServletRequest request,
            HttpServletResponse response) {
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; frame-ancestors 'none'; form-action 'self'");
        response.setHeader("Cache-Control", "no-store");
        RegisteredClient client = RlsScope.asPlatform(() -> clients.findByClientId(clientId));
        if (client == null) {
            response.setStatus(400);
            return page(request, "Unknown application", "<p>This sign-in request is not valid.</p>", "");
        }
        OAuth2AuthorizationConsent previous = principal != null
                ? RlsScope.asPlatform(() -> consents.findById(client.getId(), principal.getName()))
                : null;
        Set<String> alreadyGranted = previous != null ? previous.getScopes() : Set.of();
        Set<String> requested = new LinkedHashSet<>();
        for (String s : scope.trim().split("\\s+")) {
            if (!s.isBlank()) {
                requested.add(s);
            }
        }
        String appName = client.getClientName() != null && !client.getClientName().isBlank() ? client.getClientName() : clientId;
        StringBuilder items = new StringBuilder();
        for (String s : requested) {
            String text = SCOPE_TEXT.getOrDefault(s, "Access: " + s);
            boolean fixed = "openid".equals(s);
            boolean granted = alreadyGranted.contains(s);
            items.append("<li><label><input type=\"checkbox\" name=\"scope\" value=\"").append(esc(s)).append("\" checked")
                    .append(fixed ? " disabled" : "").append("> ").append(esc(text))
                    .append(granted ? " <span class=\"muted\">(already allowed)</span>" : "").append("</label>");
            if (fixed) {
                // A disabled box is not submitted: keep openid in the request.
                items.append("<input type=\"hidden\" name=\"scope\" value=\"openid\">");
            }
            items.append("</li>");
        }
        String body = "<p><strong>" + esc(appName) + "</strong> wants to use your account"
                + (principal != null ? " <span class=\"muted\">(" + esc(displayUser(principal.getName())) + ")</span>" : "")
                + ".</p><p>It will be able to:</p>"
                + "<form method=\"post\" action=\"/oauth2/authorize\" id=\"consent\">"
                + "<input type=\"hidden\" name=\"client_id\" value=\"" + esc(clientId) + "\">"
                + "<input type=\"hidden\" name=\"state\" value=\"" + esc(state) + "\">"
                + "<ul class=\"scopes\">" + items + "</ul>"
                + "<button type=\"submit\" id=\"allow\">Allow</button>"
                + "</form>"
                // Cancelling submits no scopes: the app is told the user said no (access_denied).
                + "<form method=\"post\" action=\"/oauth2/authorize\">"
                + "<input type=\"hidden\" name=\"client_id\" value=\"" + esc(clientId) + "\">"
                + "<input type=\"hidden\" name=\"state\" value=\"" + esc(state) + "\">"
                + "<button type=\"submit\" class=\"secondary\" id=\"cancel\">Cancel</button>"
                + "</form>"
                + "<p class=\"muted\">You can withdraw this later under Account security → Connected apps.</p>";
        return page(request, "Allow " + appName + "?", body, appName);
    }

    private static String displayUser(String principalName) {
        int sep = principalName.indexOf(':');
        return sep > 0 ? principalName.substring(sep + 1) : principalName;
    }

    /**
     * In the sign-in theme of the client's application (templates/consent.html or the theme's layout); hosted.js
     * brands the page from the client_id in this page's own URL, and translates it.
     */
    private String page(HttpServletRequest request, String title, String body, String appName) {
        return pages.render(request, new ThemePages.Page("consent", title, title, body, true, Map.of("appName", appName)));
    }

    static String esc(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
