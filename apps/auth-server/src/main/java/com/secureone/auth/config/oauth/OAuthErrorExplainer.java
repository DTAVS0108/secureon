package com.secureone.auth.config.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2ErrorAuthenticationFailureHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * Turns the authorization server's terse errors ("OAuth 2.0 Parameter: redirect_uri", a bare 400) into
 * messages that tell an integrating developer what is wrong and how to fix it, without changing the
 * protocol: error codes, redirect rules and state handling stay as RFC 6749 requires.
 *
 * <p>Registered redirect URIs are listed only outside production; client IDs are not secret, but the
 * token and client-authentication messages never reveal whether a given client exists.
 */
public final class OAuthErrorExplainer {

    private static final Logger log = LoggerFactory.getLogger(OAuthErrorExplainer.class);
    private static final String PARAMETER_PREFIX = "OAuth 2.0 Parameter: ";
    /** Spring's client-authentication providers name the failing item this way ("…: code", "…: client_id"). */
    private static final String CLIENT_AUTH_PREFIX = "Client authentication failed: ";
    private static final String DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code";

    private OAuthErrorExplainer() {}

    /** The parameter Spring named in its description, or null. */
    static String parameterOf(OAuth2Error error) {
        String description = error.getDescription();
        if (description == null) {
            return null;
        }
        if (description.startsWith(PARAMETER_PREFIX)) {
            return description.substring(PARAMETER_PREFIX.length()).trim();
        }
        if (description.startsWith(CLIENT_AUTH_PREFIX)) {
            return description.substring(CLIENT_AUTH_PREFIX.length()).trim();
        }
        return null;
    }

    private static boolean generic(OAuth2Error error) {
        return !StringUtils.hasText(error.getDescription()) || parameterOf(error) != null;
    }

    // ------------------------------------------------------------------ authorization endpoint

    /** Explanation for a refused authorization request. */
    static String explainAuthorization(OAuth2Error error, HttpServletRequest request, RegisteredClient client, boolean production) {
        if (!generic(error)) {
            return error.getDescription();
        }
        String parameter = parameterOf(error);
        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (parameter == null) {
            return defaultText(error);
        }
        switch (parameter) {
            case OAuth2ParameterNames.CLIENT_ID -> {
                if (!StringUtils.hasText(clientId)) {
                    return "The request has no client_id. Send the client ID of your app (console → OAuth clients).";
                }
                if (client == null) {
                    return "No active OAuth client has client_id '" + clientId + "'. Check the value (it is case-sensitive) "
                            + "and that the client is active (console → OAuth clients).";
                }
                return "Client '" + clientId + "' may not use the authorization code flow (its grants: "
                        + grants(client) + "). Use a client registered as a web app, single-page app or mobile app.";
            }
            case OAuth2ParameterNames.REDIRECT_URI -> {
                String redirect = request.getParameter(OAuth2ParameterNames.REDIRECT_URI);
                if (!StringUtils.hasText(redirect)) {
                    return "The request has no redirect_uri. Send the exact redirect URI registered for client '" + clientId + "'.";
                }
                String text = "redirect_uri '" + redirect + "' is not registered for client '" + clientId
                        + "'. It must match exactly: scheme, host, port and path, with no extra slash.";
                if (client != null && !production) {
                    text += " Registered: " + String.join(", ", client.getRedirectUris()) + ".";
                }
                return text + " Add it on the client in the console (OAuth clients → " + clientId + ").";
            }
            case OAuth2ParameterNames.SCOPE -> {
                Set<String> requested = scopes(request.getParameter(OAuth2ParameterNames.SCOPE));
                if (client == null) {
                    return "One of the requested scopes is not allowed.";
                }
                Set<String> unknown = new LinkedHashSet<>(requested);
                unknown.removeAll(client.getScopes());
                return (unknown.isEmpty() ? "The requested scopes are not allowed" : "Scope " + quoted(unknown) + " is not allowed")
                        + " for client '" + clientId + "'. Allowed: " + String.join(" ", client.getScopes()) + ".";
            }
            case "code_challenge" -> {
                return "Client '" + clientId + "' requires PKCE: send code_challenge = BASE64URL(SHA-256(code_verifier)) "
                        + "and code_challenge_method=S256, then send the code_verifier when exchanging the code.";
            }
            case "code_challenge_method" -> {
                return "Use code_challenge_method=S256 (plain is not accepted).";
            }
            case OAuth2ParameterNames.RESPONSE_TYPE -> {
                return "Use response_type=code (authorization code flow; implicit and hybrid flows are not supported).";
            }
            case "prompt" -> {
                return "prompt=none cannot be combined with other prompt values.";
            }
            default -> {
                return "Parameter '" + parameter + "' is missing or invalid.";
            }
        }
    }

    /**
     * Authorization endpoint error handling: redirect to the client with a clear error_description when
     * Spring validated the redirect URI, otherwise answer here (HTML page for browsers, JSON otherwise).
     */
    public static AuthenticationFailureHandler authorizationFailureHandler(RegisteredClientRepository clients, boolean production) {
        RedirectStrategy redirects = new DefaultRedirectStrategy();
        return (request, response, exception) -> {
            OAuth2Error error = ((OAuth2AuthenticationException) exception).getError();
            String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
            RegisteredClient client = StringUtils.hasText(clientId) ? clients.findByClientId(clientId) : null;
            String explanation = explainAuthorization(error, request, client, production);
            OAuth2AuthorizationCodeRequestAuthenticationToken authorization =
                    exception instanceof OAuth2AuthorizationCodeRequestAuthenticationException codeException
                            ? codeException.getAuthorizationCodeRequestAuthentication()
                            : null;
            log.info("Authorization request refused ({}): {}", error.getErrorCode(), explanation);
            if (authorization == null || !StringUtils.hasText(authorization.getRedirectUri())) {
                writeHere(request, response, error, explanation);
                return;
            }
            UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(authorization.getRedirectUri())
                    .queryParam(OAuth2ParameterNames.ERROR, error.getErrorCode())
                    .queryParam(OAuth2ParameterNames.ERROR_DESCRIPTION, UriUtils.encode(explanation, StandardCharsets.UTF_8));
            if (StringUtils.hasText(error.getUri())) {
                uri.queryParam(OAuth2ParameterNames.ERROR_URI, UriUtils.encode(error.getUri(), StandardCharsets.UTF_8));
            }
            if (StringUtils.hasText(authorization.getState())) {
                uri.queryParam(OAuth2ParameterNames.STATE, UriUtils.encode(authorization.getState(), StandardCharsets.UTF_8));
            }
            redirects.sendRedirect(request, response, uri.build(true).toUriString());
        };
    }

    /** No trustworthy redirect URI: RFC 6749 4.1.2.1 says tell the user, never redirect. */
    private static void writeHere(HttpServletRequest request, HttpServletResponse response, OAuth2Error error, String explanation)
            throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        String accept = request.getHeader("Accept");
        if (accept == null || !accept.contains("text/html")) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":" + json(error.getErrorCode()) + ",\"error_description\":" + json(explanation) + "}");
            return;
        }
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'");
        String requestId = MDC.get("requestId");
        response.getWriter().write("""
                <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="robots" content="noindex"><title>Sign-in cannot start · SecureOne</title>
                <style>body{margin:0;font-family:system-ui,sans-serif;background:#f7f7f8;color:#171717;line-height:1.5}
                main{max-width:36rem;margin:4rem auto;padding:0 1rem}.card{background:#fff;border:1px solid #e5e7eb;border-radius:14px;padding:1.5rem}
                h1{font-size:1.2rem;margin:0 0 .5rem}.dev{margin-top:1rem;padding:.8rem;border-radius:8px;background:#fef3c7;font-size:.9rem;word-break:break-word}
                code{font-size:.85em}.muted{color:#6b7280;font-size:.8rem}
                @media (prefers-color-scheme:dark){body{background:#121216;color:#ededed}.card{background:#1c1c24;border-color:#2a2a33}.dev{background:#3b2f0b}}</style>
                </head><body><main><div class="card">
                <h1>This app cannot sign you in right now</h1>
                <p>The app sent a sign-in request SecureOne cannot accept. Contact the app's support if this keeps happening.</p>
                <div class="dev"><strong>For the app's developer</strong> (<code>%s</code>)<br>%s</div>
                <p class="muted">%s</p>
                </div></main></body></html>
                """.formatted(
                HtmlUtils.htmlEscape(error.getErrorCode()),
                HtmlUtils.htmlEscape(explanation),
                requestId == null ? "" : "Request ID: " + HtmlUtils.htmlEscape(requestId)));
    }

    // ------------------------------------------------------------------ token endpoint

    /** Explanation for a refused token request (never reveals whether a client exists). */
    static String explainToken(OAuth2Error error, HttpServletRequest request) {
        if (!generic(error)) {
            return error.getDescription();
        }
        String grant = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        String parameter = parameterOf(error);
        return switch (error.getErrorCode()) {
            case OAuth2ErrorCodes.INVALID_GRANT -> {
                if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grant)) {
                    yield "The refresh token is invalid, expired or revoked, or was already used: refresh tokens rotate, "
                            + "so always keep the newest one.";
                }
                if (AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(grant)) {
                    yield "The authorization code is invalid, expired or already used, or redirect_uri / code_verifier do not "
                            + "match the authorization request. Codes are single-use and short-lived: exchange them immediately.";
                }
                yield "The grant is invalid or expired.";
            }
            case OAuth2ErrorCodes.UNAUTHORIZED_CLIENT -> "This client is not allowed to use grant_type '" + grant
                    + "'. Check the client's grant types in the console.";
            case OAuth2ErrorCodes.UNSUPPORTED_GRANT_TYPE -> "grant_type '" + grant + "' is not supported. Supported: "
                    + "authorization_code, refresh_token, client_credentials, " + DEVICE_CODE
                    + ", urn:ietf:params:oauth:grant-type:token-exchange.";
            case OAuth2ErrorCodes.INVALID_SCOPE -> "A requested scope is not allowed for this client: request only scopes "
                    + "registered on the client.";
            case OAuth2ErrorCodes.INVALID_REQUEST -> parameter == null
                    ? defaultText(error)
                    : "Parameter '" + parameter + "' is missing, repeated or invalid.";
            default -> defaultText(error);
        };
    }

    /** Token endpoint: same JSON error response, with a useful error_description. */
    public static AuthenticationFailureHandler tokenFailureHandler() {
        OAuth2ErrorAuthenticationFailureHandler json = new OAuth2ErrorAuthenticationFailureHandler();
        return (request, response, exception) -> json.onAuthenticationFailure(request, response, explained(exception, explainToken(error(exception), request)));
    }

    /**
     * Client authentication (token, revoke, introspect, PAR, device endpoints). Like Spring's own handler,
     * invalid_client is 401 (RFC 6749 5.2) and everything else 400.
     */
    public static AuthenticationFailureHandler clientAuthenticationFailureHandler() {
        HttpMessageConverter<OAuth2Error> converter = new OAuth2ErrorHttpMessageConverter();
        return (request, response, exception) -> {
            OAuth2Error error = error(exception);
            boolean invalidClient = OAuth2ErrorCodes.INVALID_CLIENT.equals(error.getErrorCode());
            String description = invalidClient && generic(error)
                    ? "Client authentication failed. Check the client ID and secret, and send them with HTTP Basic "
                            + "(client_secret_basic). Public clients (single-page, mobile, device apps) send only client_id."
                    : explainToken(error, request);
            SecurityContextHolder.clearContext();
            ServletServerHttpResponse http = new ServletServerHttpResponse(response);
            http.setStatusCode(invalidClient ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST);
            converter.write(new OAuth2Error(error.getErrorCode(), description, error.getUri()), null, http);
        };
    }

    // ------------------------------------------------------------------ helpers

    private static OAuth2Error error(AuthenticationException exception) {
        return exception instanceof OAuth2AuthenticationException oauth
                ? oauth.getError()
                : new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, exception.getMessage(), null);
    }

    private static OAuth2AuthenticationException explained(AuthenticationException exception, String description) {
        OAuth2Error error = error(exception);
        return new OAuth2AuthenticationException(new OAuth2Error(error.getErrorCode(), description, error.getUri()), exception);
    }

    private static String defaultText(OAuth2Error error) {
        return StringUtils.hasText(error.getDescription()) ? error.getDescription() : "The request was refused (" + error.getErrorCode() + ").";
    }

    private static String grants(RegisteredClient client) {
        return client.getAuthorizationGrantTypes().stream().map(AuthorizationGrantType::getValue).sorted().collect(Collectors.joining(", "));
    }

    private static Set<String> scopes(String scope) {
        return scope == null ? Set.of() : new LinkedHashSet<>(Arrays.asList(scope.trim().split("\\s+")));
    }

    private static String quoted(Set<String> values) {
        List<String> list = values.stream().map(v -> "'" + v + "'").toList();
        return String.join(", ", list);
    }

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"";
    }
}
