package com.secureone.auth.config;

import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import com.secureone.auth.config.oauth.DeviceCodeGrantGuard;
import com.secureone.auth.federation.SamlAwareOidcLogoutHandler;
import com.secureone.auth.federation.SamlLogoutService;
import com.secureone.auth.config.oauth.DeviceCodeLookup;
import com.secureone.auth.config.oauth.DeviceConsentDenyGuard;
import com.secureone.auth.config.oauth.DeviceVerificationTenantGuard;
import com.secureone.auth.config.oauth.PublicClientDeviceAuthentication;
import com.secureone.auth.config.oauth.PublicClientRefreshAuthentication;
import com.secureone.auth.oauth.OAuthTokenExchangeFilter;
import com.secureone.auth.serviceaccount.ApiKeyAuthenticationFilter;
import com.secureone.auth.admin.AdminBasicAuthenticationFilter;
import com.secureone.auth.admin.AdminCredentialAuthenticationFilter;
import com.secureone.auth.application.ApplicationOAuthClientResolver;
import com.secureone.auth.authn.DevAdminAuthenticationProvider;
import com.secureone.auth.authn.FormLoginFailureHandler;
import com.secureone.auth.authn.LoginSuccessHandler;
import com.secureone.auth.authn.OAuthAwareLoginEntryPoint;
import com.secureone.auth.authn.TenantLoginUsernameFilter;
import com.secureone.auth.authn.TenantPasswordAuthenticationProvider;
import com.secureone.auth.config.oauth.ClientTenantAuthorizationValidator;
import com.secureone.auth.tenant.RlsScope;
import jakarta.servlet.DispatcherType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2DeviceCodeAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2DeviceAuthorizationConsentAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2DeviceVerificationAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Security wiring for the authorization server.
 *
 * <p>Two ordered filter chains:
 * <ol>
 *   <li>The OAuth2/OIDC protocol endpoints (authorize, token, jwks, discovery, ...) — matched by
 *       the authorization-server endpoints matcher, with OIDC enabled, and protected as a resource
 *       server (JWT) for OIDC UserInfo / Client Registration.</li>
 *   <li>Stateless Bearer-token APIs for integrated apps ({@code /api/v1/account/profile}, password change).</li>
 *   <li>The default application chain governing everything else, with form login.</li>
 * </ol>
 */
@Configuration
public class SecurityConfig {

    @Bean
    OAuthAwareLoginEntryPoint oauthAwareLoginEntryPoint(
            RequestCache requestCache, ApplicationOAuthClientResolver applications, DeviceCodeLookup deviceCodes) {
        return new OAuthAwareLoginEntryPoint(requestCache, applications, deviceCodes);
    }

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            RequestCache requestCache,
            OAuthAwareLoginEntryPoint oauthAwareLoginEntryPoint,
            Function<OidcUserInfoAuthenticationContext, OidcUserInfo> oidcUserInfoMapper,
            OAuthTokenExchangeFilter tokenExchangeFilter,
            ClientTenantAuthorizationValidator clientTenantValidator,
            RegisteredClientRepository registeredClients,
            DeviceCodeLookup deviceCodes,
            OAuth2AuthorizationConsentService consentService,
            OAuth2AuthorizationService authorizationService,
            org.springframework.data.redis.core.StringRedisTemplate redis,
            SamlLogoutService samlLogout,
            SecureOneEnvironmentProperties environment)
            throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer =
                new OAuth2AuthorizationServerConfigurer();
        RequestMatcher endpointsMatcher = authorizationServer.getEndpointsMatcher();

        http
                .securityMatcher(endpointsMatcher)
                .cors(Customizer.withDefaults())
                .requestCache(cache -> cache.requestCache(requestCache))
                .sessionManagement(session -> session.sessionFixation(fix -> fix.migrateSession()))
                .with(authorizationServer, server -> server
                        // RFC 9126 pushed authorization requests: client-authenticated, advertised in metadata.
                        .pushedAuthorizationRequestEndpoint(Customizer.withDefaults())
                        // SPAs (public clients) may refresh with client_id alone; see PublicClientRefreshAuthentication.
                        // Devices (TVs, CLIs) registered as public clients use the device grant the same way.
                        .clientAuthentication(client -> client
                                // Failed client authentication says what to check (without revealing whether the client exists).
                                .errorResponseHandler(com.secureone.auth.config.oauth.OAuthErrorExplainer.clientAuthenticationFailureHandler())
                                .authenticationConverter(new PublicClientRefreshAuthentication.Converter())
                                .authenticationConverter(new PublicClientDeviceAuthentication.Converter())
                                .authenticationProviders(providers -> {
                                    providers.add(0, new PublicClientRefreshAuthentication.Provider(registeredClients));
                                    providers.add(0, new PublicClientDeviceAuthentication.Provider(registeredClients));
                                }))
                        // RFC 8628 device authorization grant: the user enters the code on the hosted page.
                        .deviceAuthorizationEndpoint(device -> device
                                .verificationUri(DEVICE_PAGE)
                                .deviceAuthorizationResponseHandler(SecurityConfig::writeDeviceAuthorization))
                        // device_code polling: slow_down when too fast, replay of a used code revokes its tokens.
                        .tokenEndpoint(token -> token
                                .errorResponseHandler(com.secureone.auth.config.oauth.OAuthErrorExplainer.tokenFailureHandler())
                                .authenticationProviders(providers -> providers.replaceAll(provider ->
                                        provider instanceof OAuth2DeviceCodeAuthenticationProvider
                                                ? new DeviceCodeGrantGuard(provider, authorizationService, redis)
                                                : provider)))
                        // Approval is always an explicit "Allow" (POST with the per-request state) on the
                        // device page, never a bare link: a signed-in user who follows an attacker's
                        // verification link must not connect the attacker's device to their account.
                        .deviceVerificationEndpoint(verification -> verification
                                .consentPage(DEVICE_PAGE)
                                .authenticationProviders(providers -> providers.replaceAll(provider -> {
                                    if (provider instanceof OAuth2DeviceVerificationAuthenticationProvider verify) {
                                        verify.setAuthorizationConsentRequired(context -> true);
                                        return new DeviceVerificationTenantGuard(verify, deviceCodes, clientTenantValidator);
                                    }
                                    if (provider instanceof OAuth2DeviceAuthorizationConsentAuthenticationProvider) {
                                        return new DeviceConsentDenyGuard(provider, registeredClients, consentService);
                                    }
                                    return provider;
                                }))
                                .deviceVerificationResponseHandler((request, response, authentication) ->
                                        response.sendRedirect(DEVICE_PAGE + "?status=approved"))
                                .errorResponseHandler((request, response, exception) ->
                                        response.sendRedirect(DEVICE_PAGE + "?status=" + deviceErrorStatus(exception))))
                        .authorizationEndpoint(endpoint -> endpoint
                                // Integration mistakes (unregistered redirect_uri, scope, missing PKCE) explained.
                                .errorResponseHandler(com.secureone.auth.config.oauth.OAuthErrorExplainer
                                        .authorizationFailureHandler(registeredClients, environment.isProduction()))
                                .authenticationProviders(providers ->
                                providers.forEach(provider -> {
                                    if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider codeProvider) {
                                        // Users may only authorize clients of their own tenant and app.
                                        codeProvider.setAuthenticationValidator(clientTenantValidator);
                                    }
                                })))
                        .oidc(oidc -> oidc
                                .userInfoEndpoint(userInfo -> userInfo.userInfoMapper(oidcUserInfoMapper))
                                // Sessions from a SAML IdP are also signed out there (single logout).
                                .logoutEndpoint(logout -> logout.logoutResponseHandler(new SamlAwareOidcLogoutHandler(samlLogout)))))
                .addFilterBefore(tokenExchangeFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/oauth2/token", "/oauth2/revoke", "/oauth2/introspect")
                        .permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
                // Redirect the authorize endpoint and explicit browser (text/html) requests to the login
                // page when unauthenticated. API clients sending Accept: */* (UserInfo, device
                // authorization) get a 401 with WWW-Authenticate instead of a login page.
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                oauthAwareLoginEntryPoint, browserNavigation()))
                // Accept access tokens for OIDC UserInfo and/or Client Registration.
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.decoder(jwtDecoder)));
        return http.build();
    }

    private static RequestMatcher browserNavigation() {
        MediaTypeRequestMatcher html = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        html.setIgnoredMediaTypes(java.util.Set.of(MediaType.ALL));
        return request -> "/oauth2/authorize".equals(request.getRequestURI()) || html.matches(request);
    }

    /**
     * RFC 8628 section 3.2 response. Spring's default leaves out {@code interval}; devices then fall back to
     * 5 seconds, but saying it explicitly is what the slow_down rule is measured against.
     */
    private static void writeDeviceAuthorization(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            org.springframework.security.core.Authentication authentication)
            throws java.io.IOException {
        var result = (org.springframework.security.oauth2.server.authorization.authentication
                .OAuth2DeviceAuthorizationRequestAuthenticationToken) authentication;
        String verificationUri = org.springframework.web.servlet.support.ServletUriComponentsBuilder
                .fromContextPath(request).path(DEVICE_PAGE).build().toUriString();
        String userCode = result.getUserCode().getTokenValue();
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("device_code", result.getDeviceCode().getTokenValue());
        body.put("user_code", userCode);
        body.put("verification_uri", verificationUri);
        body.put("verification_uri_complete", verificationUri + "?user_code=" + userCode);
        body.put("expires_in", java.time.Duration.between(
                java.time.Instant.now(), result.getDeviceCode().getExpiresAt()).toSeconds());
        body.put("interval", DeviceCodeGrantGuard.INTERVAL_SECONDS);
        response.setStatus(200);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body));
    }

    /** Result shown on the device page; a fixed set of values, never text from the request. */
    private static String deviceErrorStatus(Exception exception) {
        if (!(exception instanceof OAuth2AuthenticationException oauth)) {
            return "error";
        }
        String code = oauth.getError().getErrorCode();
        if ("access_denied".equals(code)) {
            // The tenant/app rule (DeviceVerificationTenantGuard) vs the user pressing "Deny".
            return DeviceVerificationTenantGuard.NOT_ALLOWED.equals(oauth.getError().getDescription())
                    ? "not_allowed"
                    : "denied";
        }
        return "invalid_grant".equals(code) || "invalid_request".equals(code) ? "invalid_grant" : "error";
    }

    /** Hosted page where a user types the code shown on a device (RFC 8628 verification_uri). */
    static final String DEVICE_PAGE = "/account/device.html";

    @Bean
    @Order(2)
    SecurityFilterChain bearerApiSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        RequestMatcher bearerPrivacy = request -> {
            String uri = request.getRequestURI();
            return uri.equals("/api/v1/account/privacy/export")
                    || uri.equals("/api/v1/account/privacy/delete-request");
        };
        http
                .securityMatcher(request -> {
                    String uri = request.getRequestURI();
                    return uri.equals("/api/v1/account/profile")
                            || uri.equals("/api/v1/account/password/change")
                            || bearerPrivacy.matches(request);
                })
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.decoder(jwtDecoder)))
                .addFilterAfter(new BearerTenantContextFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    AuthenticationManager authenticationManager(
            ObjectProvider<DevAdminAuthenticationProvider> devAdminProvider,
            TenantPasswordAuthenticationProvider tenantPasswordProvider) {
        List<AuthenticationProvider> providers = new ArrayList<>();
        devAdminProvider.ifAvailable(providers::add);
        providers.add(tenantPasswordProvider);
        ProviderManager delegate = new ProviderManager(providers);
        // Credential checks look users up before any tenant is known, so they run in platform RLS scope.
        return authentication -> RlsScope.asPlatform(() -> delegate.authenticate(authentication));
    }

    @Bean
    @Order(3)
    SecurityFilterChain m2mSecurityFilterChain(HttpSecurity http, ApiKeyAuthenticationFilter apiKeyAuthenticationFilter)
            throws Exception {
        http
                .securityMatcher("/api/v1/m2m/**")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("M2M"))
                // Missing or invalid API key is 401 (not authenticated), not 403.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                                org.springframework.http.HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(apiKeyAuthenticationFilter, AbstractPreAuthenticatedProcessingFilter.class);
        return http.build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain defaultSecurityFilterChain(
            HttpSecurity http,
            RequestCache requestCache,
            AuthenticationManager authenticationManager,
            AdminBasicAuthenticationFilter adminBasicAuthenticationFilter,
            AdminCredentialAuthenticationFilter adminCredentialAuthenticationFilter,
            TenantLoginUsernameFilter tenantLoginUsernameFilter,
            LoginSuccessHandler loginSuccessHandler,
            FormLoginFailureHandler loginFailureHandler,
            @org.springframework.beans.factory.annotation.Value("${server.port:9000}") int serverPort,
            @org.springframework.beans.factory.annotation.Value("${management.server.port:}") String managementPort)
            throws Exception {
        // With SECUREONE_MANAGEMENT_PORT set, actuator (health, info, Prometheus) is served only on that
        // port, which is never published (probes and the metrics scraper reach it inside the cluster).
        int separateManagementPort = managementPort.isBlank() || managementPort.equals(Integer.toString(serverPort))
                ? -1
                : Integer.parseInt(managementPort.trim());
        http
                .cors(Customizer.withDefaults())
                .requestCache(cache -> cache.requestCache(requestCache))
                .sessionManagement(session -> session.sessionFixation(fix -> fix.migrateSession()))
                .authenticationManager(authenticationManager)
                .authorizeHttpRequests(auth -> auth
                        // Let 4xx/5xx reach the client as-is instead of being re-challenged as 401 on /error.
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(request -> separateManagementPort > 0
                                && request.getLocalPort() == separateManagementPort)
                        .permitAll()
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/docs",
                                "/docs/**",
                                "/api/info",
                                "/api/v1/confluence",
                                "/api/v1/setup/**",
                                "/api/v1/account/password/forgot",
                                "/api/v1/account/password/reset",
                                "/api/v1/account/password/reset/otp",
                                "/api/v1/account/set-password",
                                "/api/v1/account/email/**",
                                "/api/v1/account/verify/**",
                                "/api/v1/account/magic-link/**",
                                "/api/v1/auth/methods",
                                "/api/v1/auth/session/login",
                                "/api/v1/auth/mfa/**",
                                "/api/v1/auth/federation/**",
                                "/api/v1/auth/impersonation/consume",
                                "/api/v1/auth/device/**",
                                "/.well-known/oauth-protected-resource",
                                "/api/v1/auth/passkey/**",
                                "/scim/v2/**",
                                "/api/v1/applications/**",
                                "/oauth2/backchannel-logout",
                                "/account/**",
                                "/login",
                                "/login.html")
                        .permitAll()
                        .requestMatchers("/api/admin/v1/auth/login", "/api/admin/v1/auth/logout", "/api/admin/v1/auth/mfa/**")
                        .permitAll()
                        .requestMatchers("/api/admin/v1/**").authenticated()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/api/admin/v1/**",
                        "/api/v1/setup/**",
                        "/api/v1/account/**",
                        "/api/v1/auth/methods",
                        "/api/v1/auth/me",
                        "/api/v1/auth/session/login",
                        "/api/v1/auth/mfa/**",
                        "/api/v1/auth/federation/**",
                        "/api/v1/auth/impersonation/consume",
                        "/api/v1/auth/device/**",
                        "/api/v1/auth/passkey/**",
                        "/scim/v2/**",
                        "/api/v1/applications/**",
                        "/login"))
                .httpBasic(Customizer.withDefaults())
                .addFilterBefore(adminCredentialAuthenticationFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(adminBasicAuthenticationFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(tenantLoginUsernameFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .failureHandler(loginFailureHandler)
                        .successHandler(loginSuccessHandler)
                        .permitAll());
        return http.build();
    }
}
