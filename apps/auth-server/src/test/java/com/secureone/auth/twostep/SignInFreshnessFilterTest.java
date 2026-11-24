package com.secureone.auth.twostep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.secureone.auth.oauth.OAuthClient;
import com.secureone.auth.oauth.OAuthClientRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SignInFreshnessFilterTest {

    private static final String CALLBACK = "https://shop.example.com/callback";

    @SuppressWarnings("unchecked")
    private static SignInFreshnessFilter filter() {
        OAuthClient client = new OAuthClient();
        client.setClientId("shop");
        client.setRedirectUris(List.of(CALLBACK));
        OAuthClientRepository repository = mock(OAuthClientRepository.class);
        when(repository.findByClientId("shop")).thenReturn(Optional.of(client));
        ObjectProvider<OAuthClientRepository> clients = mock(ObjectProvider.class);
        when(clients.getObject()).thenReturn(repository);
        return new SignInFreshnessFilter(Duration.ofHours(24), mock(ObjectProvider.class), clients, mock(ObjectProvider.class));
    }

    private static MockHttpServletRequest authorize(String redirectUri, String prompt) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setParameter("client_id", "shop");
        request.setParameter("redirect_uri", redirectUri);
        request.setParameter("state", "s 1");
        request.setParameter("prompt", prompt);
        return request;
    }

    @Test
    void promptNoneWhenSignedOutAnswersLoginRequiredToTheRegisteredRedirectUri() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter().doFilter(authorize(CALLBACK, "none"), response, chain);
        assertNull(chain.getRequest(), "the sign-in page is never reached");
        String location = response.getRedirectedUrl();
        assertNotNull(location);
        assertTrue(location.startsWith(CALLBACK + "?error=login_required&"), location);
        assertTrue(location.endsWith("&state=s+1"), location);
    }

    @Test
    void promptNoneNeverRedirectsToAnUnregisteredUri() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter().doFilter(authorize("https://evil.example/cb", "none"), response, chain);
        assertNull(response.getRedirectedUrl());
        assertNotNull(chain.getRequest(), "the authorization server explains the bad redirect_uri");
    }

    @Test
    void promptValuesAndLoginRemovedFromTheContinueUrl() {
        assertEquals(Set.of("login", "consent"), SignInFreshnessFilter.promptValues(" login  consent login"));
        assertEquals(Set.of(), SignInFreshnessFilter.promptValues(null));
        MockHttpServletRequest request = authorize(CALLBACK, "login");
        String url = SignInFreshnessFilter.withoutPromptLogin(request);
        assertTrue(url.startsWith("/oauth2/authorize?"), url);
        assertTrue(!url.contains("prompt="), url);
        request.setParameter("prompt", "login consent");
        assertTrue(SignInFreshnessFilter.withoutPromptLogin(request).contains("prompt=consent"));
    }

    private static String unsignedJwt(String payloadJson) {
        java.util.Base64.Encoder b64 = java.util.Base64.getUrlEncoder().withoutPadding();
        return b64.encodeToString("{\"alg\":\"RS256\"}".getBytes()) + "." + b64.encodeToString(payloadJson.getBytes()) + ".sig";
    }

    @Test
    @SuppressWarnings("unchecked")
    void idTokenHintMustNameTheSignedInUser() {
        java.util.UUID una = java.util.UUID.randomUUID();
        com.secureone.auth.mfa.MfaLoginService mfa = mock(com.secureone.auth.mfa.MfaLoginService.class);
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.TestingAuthenticationToken("acme:una@acme.test", "x");
        when(mfa.userIdOf(auth)).thenReturn(una);
        ObjectProvider<com.secureone.auth.mfa.MfaLoginService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(mfa);
        SignInFreshnessFilter f = new SignInFreshnessFilter(Duration.ofHours(24), provider, mock(ObjectProvider.class), mock(ObjectProvider.class));

        assertTrue(f.hintMatches(unsignedJwt("{\"sub\":\"" + una + "\",\"user_id\":\"" + una + "\"}"), auth), "same user");
        assertTrue(f.hintMatches(unsignedJwt("{\"sub\":\"acme:una@acme.test\"}"), auth), "legacy subject of the same user");
        assertTrue(!f.hintMatches(unsignedJwt("{\"sub\":\"x\",\"user_id\":\"" + java.util.UUID.randomUUID() + "\"}"), auth), "another user");
        assertTrue(!f.hintMatches(unsignedJwt("{\"sub\":\"acme:vic@acme.test\"}"), auth), "another user's legacy subject");
        assertTrue(f.hintMatches("not-a-jwt", auth), "unreadable hints are left to the authorization server");
        assertNull(SignInFreshnessFilter.unverifiedClaims("a.%%%.c"));
    }

    @Test
    void theSignInPageReturnsToTheRequestWithoutPromptLogin() {
        MockHttpServletRequest request = authorize(CALLBACK, "login consent");
        request.setQueryString("client_id=shop&prompt=login+consent");
        var returnTo = com.secureone.auth.authn.OAuthLoginRedirectSupport.withoutPromptLoginRequest(request);
        assertEquals("consent", returnTo.getParameter("prompt"));
        assertTrue(!returnTo.getQueryString().contains("login"), returnTo.getQueryString());
        assertEquals("shop", returnTo.getParameter("client_id"));
    }
}
