package com.secureone.auth.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class UserAvatarServiceTest {

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    @Test
    void imageTypesAreRecognisedByTheirBytes() {
        assertEquals(Optional.of("image/png"), UserAvatarService.detectType(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0)));
        assertEquals(Optional.of("image/jpeg"), UserAvatarService.detectType(bytes(0xFF, 0xD8, 0xFF, 0xE0)));
        assertEquals(Optional.of("image/webp"),
                UserAvatarService.detectType("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void otherContentIsRefusedWhateverItClaimsToBe() {
        assertEquals(Optional.empty(), UserAvatarService.detectType(
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Optional.empty(), UserAvatarService.detectType("GIF89a".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(Optional.empty(), UserAvatarService.detectType(bytes(0x89, 'P', 'N')));
        assertEquals(Optional.empty(), UserAvatarService.detectType(new byte[0]));
    }

    @Test
    void uploadsAreCheckedBeforeAnythingIsStored() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UserAvatarService service = new UserAvatarService(jdbc);
        UserAccount user = new UserAccount();

        var tooBig = assertThrows(ResponseStatusException.class,
                () -> service.save(user, new byte[UserAvatarService.MAX_BYTES + 1]));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, tooBig.getStatusCode());

        var notImage = assertThrows(ResponseStatusException.class,
                () -> service.save(user, "<svg/>".getBytes(StandardCharsets.UTF_8)));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, notImage.getStatusCode());

        var empty = assertThrows(ResponseStatusException.class, () -> service.save(user, new byte[0]));
        assertEquals(HttpStatus.BAD_REQUEST, empty.getStatusCode());

        verify(jdbc, never()).update(anyString(), (Object[]) org.mockito.ArgumentMatchers.any());
    }
}
