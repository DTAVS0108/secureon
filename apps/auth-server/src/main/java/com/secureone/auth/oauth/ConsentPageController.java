package com.secureone.auth.oauth;

import com.secureone.auth.tenant.RlsScope;
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

    public ConsentPageController(RegisteredClientRepository clients, OAuth2AuthorizationConsentService consents) {
        this.clients = clients;
        this.consents = consents;
    }

    @GetMapping(value = PATH, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String consent(
            Principal principal,
            @RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
            @RequestParam(OAuth2ParameterNames.SCOPE) String scope,
            @RequestParam(OAuth2ParameterNames.STATE) String state,
            HttpServletResponse response) {
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; frame-ancestors 'none'; form-action 'self'");
        response.setHeader("Cache-Control", "no-store");
        RegisteredClient client = RlsScope.asPlatform(() -> clients.findByClientId(clientId));
        if (client == null) {
            response.setStatus(400);
            return page("Unknown application", "<p>This sign-in request is not valid.</p>", null);
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
        return page("Allow " + appName + "?", body, clientId);
    }

    private static String displayUser(String principalName) {
        int sep = principalName.indexOf(':');
        return sep > 0 ? principalName.substring(sep + 1) : principalName;
    }

    /** hosted.js brands the page from the client_id in this page's own URL, and translates it. */
    private static String page(String title, String body, String clientId) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + esc(title) + " · SecureOne</title>"
                + "<style>body{font-family:system-ui,sans-serif;max-width:26rem;margin:3rem auto;padding:0 1rem;color:#111}"
                + "ul.scopes{list-style:none;padding:0;margin:1rem 0}ul.scopes li{padding:.4rem 0;border-bottom:1px solid #f1f1f1}"
                + "button{width:100%;padding:.6rem;margin-top:.5rem;background:#4f46e5;color:#fff;border:0;border-radius:8px;font-weight:600;cursor:pointer}"
                + "button.secondary{background:#fff;color:#111;border:1px solid #d1d5db}.muted{color:#6b7280;font-size:.85rem}</style>"
                + "<script src=\"/account/i18n.js\"></script><script src=\"/account/hosted.js\"></script>"
                + "</head><body><h1>" + esc(title) + "</h1>" + body + "</body></html>";
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
