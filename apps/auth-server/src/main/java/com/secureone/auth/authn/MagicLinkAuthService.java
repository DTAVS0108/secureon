package com.secureone.auth.authn;

import com.secureone.auth.account.AccountNotificationService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MagicLinkAuthService {

    private final AccountNotificationService accounts;
    private final UserAccountRepository users;
    private final TenantUserDetailsService userDetailsService;
    private final TenantSlugResolver tenantSlugResolver;
    private final SecurityContextRepository securityContextRepository;
    private final LoginSuccessHandler loginSuccessHandler;

    public MagicLinkAuthService(
            AccountNotificationService accounts,
            UserAccountRepository users,
            TenantUserDetailsService userDetailsService,
            TenantSlugResolver tenantSlugResolver,
            SecurityContextRepository securityContextRepository,
            LoginSuccessHandler loginSuccessHandler) {
        this.accounts = accounts;
        this.users = users;
        this.userDetailsService = userDetailsService;
        this.tenantSlugResolver = tenantSlugResolver;
        this.securityContextRepository = securityContextRepository;
        this.loginSuccessHandler = loginSuccessHandler;
    }

    public Map<String, String> completeAndCreateSession(
            String rawToken, HttpServletRequest request, HttpServletResponse response) {
        Map<String, String> result = accounts.completeMagicLink(rawToken);
        UserAccount user = users.findByEmailIgnoreCase(result.get("loginEmail")).stream()
                .findFirst()
                .orElseThrow();
        String tenantSlug = tenantSlugResolver.slugForTenant(user.getTenantId());
        UserDetails details = userDetailsService.buildSessionPrincipal(tenantSlug, user);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        loginSuccessHandler.recordSuccessfulLogin(request, authentication);
        result.put("loginUsername", tenantSlug + ":" + user.getEmail());
        result.put("sessionCreated", "true");
        return result;
    }

    public ResponseEntity<?> completeAndCreateSessionApi(
            String rawToken, HttpServletRequest request, HttpServletResponse response) {
        Map<String, String> result = completeAndCreateSession(rawToken, request, response);
        return ResponseEntity.ok(Map.of(
                "signedIn", true,
                "email", result.get("loginEmail"),
                "loginUsername", result.get("loginUsername")));
    }
}
