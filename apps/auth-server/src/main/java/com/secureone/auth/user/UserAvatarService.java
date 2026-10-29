package com.secureone.auth.user;

import com.secureone.auth.tenant.RlsScope;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Profile pictures (platform.user_avatar). Callers resolve and authorize the user first; the rows are read and
 * written as the platform because the caller's tenant context is not always the user's.
 */
@Service
public class UserAvatarService {

    public static final int MAX_BYTES = 512 * 1024;

    public record Avatar(String contentType, byte[] image, Instant updatedAt) {}

    private final JdbcTemplate jdbc;

    public UserAvatarService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Avatar> find(UUID userId) {
        return RlsScope.asPlatform(() -> jdbc.query(
                "SELECT content_type, image, updated_at FROM platform.user_avatar WHERE user_id = ?",
                (rs, i) -> new Avatar(rs.getString(1), rs.getBytes(2), rs.getTimestamp(3).toInstant()),
                userId).stream().findFirst());
    }

    /** When the picture last changed (for cache-busting URLs), without loading the image. */
    public Optional<Instant> version(UUID userId) {
        return RlsScope.asPlatform(() -> jdbc.query(
                "SELECT updated_at FROM platform.user_avatar WHERE user_id = ?",
                (rs, i) -> rs.getTimestamp(1).toInstant(),
                userId).stream().findFirst());
    }

    /** Stores the picture after checking its size and that its bytes really are PNG, JPEG or WebP. */
    public Instant save(UserAccount user, byte[] image) {
        if (image == null || image.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an image.");
        }
        if (image.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The picture must be 512 KB or smaller.");
        }
        String type = detectType(image)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use a PNG, JPEG or WebP image."));
        Instant now = Instant.now();
        RlsScope.runAsPlatform(() -> jdbc.update("""
                INSERT INTO platform.user_avatar (user_id, tenant_id, content_type, image, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE
                SET content_type = EXCLUDED.content_type, image = EXCLUDED.image, updated_at = EXCLUDED.updated_at
                """, user.getId(), user.getTenantId(), type, image, Timestamp.from(now)));
        return now;
    }

    public boolean delete(UUID userId) {
        return RlsScope.asPlatform(() -> jdbc.update("DELETE FROM platform.user_avatar WHERE user_id = ?", userId)) > 0;
    }

    static Optional<String> detectType(byte[] b) {
        if (startsWith(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("image/png");
        }
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] b, int offset, int... expected) {
        if (b.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((b[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
