package com.secureone.auth.authn;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The session authentication for a sign-in completed outside Spring's form login (OTP, magic link,
 * federation, MFA, passkey). It carries the factor that completed the sign-in, stamped now: the
 * authorization server reads the ID token's auth_time from those factor authorities and refuses to
 * issue one without any (a 500 at the token endpoint).
 */
public final class SignedInAuthentication {

    /** Second factor completed through the MFA step (TOTP or a recovery code). */
    public static final String MFA_FACTOR = "FACTOR_MFA";

    private SignedInAuthentication() {}

    public static Authentication of(UserDetails user, String... factorAuthorities) {
        List<GrantedAuthority> authorities = new ArrayList<>(user.getAuthorities());
        for (String factor : factorAuthorities) {
            authorities.add(FactorGrantedAuthority.fromAuthority(factor));
        }
        return UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);
    }
}
