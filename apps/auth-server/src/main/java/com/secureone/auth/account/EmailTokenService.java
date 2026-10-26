package com.secureone.auth.account;

import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.notify.EmailToken;
import com.secureone.auth.notify.EmailTokenRepository;
import com.secureone.auth.notify.EmailTokenType;
import com.secureone.auth.user.UserAccount;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmailTokenService {

    public record IssuedToken(String rawToken, EmailToken entity) {}

    private final EmailTokenRepository tokens;

    public EmailTokenService(EmailTokenRepository tokens) {
        this.tokens = tokens;
    }

    public IssuedToken issue(UserAccount user, EmailTokenType type) {
        tokens.deleteActiveByUserIdAndType(user.getId(), type);
        String raw = TokenHasher.generateRawToken();
        EmailToken token = new EmailToken();
        token.setUserId(user.getId());
        token.setType(type);
        token.setTokenHash(TokenHasher.hash(raw));
        token.setExpiresAt(Instant.now().plus(ttlHours(type), ChronoUnit.HOURS));
        tokens.save(token);
        return new IssuedToken(raw, token);
    }

    @Transactional(readOnly = true)
    public EmailToken requireValid(String rawToken, EmailTokenType type) {
        String hash = TokenHasher.hash(rawToken);
        EmailToken token = tokens
                .findByTokenHashAndTypeAndConsumedAtIsNull(hash, type)
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired link."));
        return token;
    }

    public void consume(EmailToken token) {
        token.setConsumedAt(Instant.now());
        tokens.save(token);
    }

    private static long ttlHours(EmailTokenType type) {
        return switch (type) {
            case VERIFY_EMAIL -> 24;
            case RESET_PASSWORD, SET_PASSWORD -> 1;
            case MAGIC_LINK, CHANGE_EMAIL -> 1;
        };
    }
}
