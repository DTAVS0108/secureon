package com.secureone.auth.notify;

import com.secureone.auth.config.SecureOneEnvironmentProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The encryption key kept outside the database, in this order: {@code SECUREONE_ENCRYPTION_KEY}; the key file
 * {@code <SECUREONE_DATA_DIR>/encryption.key}; a new key file, created on the first start when
 * {@code SECUREONE_DATA_DIR} (default {@code /var/lib/secureone}) is a mounted volume. A key written into the
 * container's own filesystem would be lost with the container, so nothing is generated without a volume.
 *
 * <p>Production needs one of the three; development falls back to a key in the database (see
 * {@link SecretProtector}).
 */
@Component
public class EncryptionKeySource {

    private static final Logger log = LoggerFactory.getLogger(EncryptionKeySource.class);
    static final String FILE_NAME = "encryption.key";

    public enum Origin {
        /** SECUREONE_ENCRYPTION_KEY */
        ENVIRONMENT,
        /** A key file that already existed. */
        FILE,
        /** A key file created by this start. */
        GENERATED,
        /** No key outside the database (development only). */
        NONE
    }

    private final String value;
    private final Origin origin;
    private final Path file;

    public EncryptionKeySource(
            @Value("${SECUREONE_ENCRYPTION_KEY:}") String environmentKey,
            @Value("${SECUREONE_DATA_DIR:/var/lib/secureone}") String dataDir,
            SecureOneEnvironmentProperties environment) {
        this.file = Path.of(dataDir).toAbsolutePath().normalize().resolve(FILE_NAME);
        if (environmentKey != null && !environmentKey.isBlank()) {
            this.value = environmentKey.trim();
            this.origin = Origin.ENVIRONMENT;
            return;
        }
        String stored = read(file);
        if (stored != null) {
            this.value = stored;
            this.origin = Origin.FILE;
            return;
        }
        if (isMountPoint(file.getParent()) && Files.isWritable(file.getParent())) {
            boolean createdHere = create(file);
            this.value = read(file);
            this.origin = createdHere ? Origin.GENERATED : Origin.FILE;
            if (createdHere) {
                String banner = "=".repeat(78);
                log.warn("\n{}\n New encryption key created in {}.\n It protects the secrets SecureOne stores. Back it up"
                        + " now, separately from the database:\n without it, a database backup cannot be used. First-run"
                        + " setup shows it once.\n{}", banner, file, banner);
            }
            return;
        }
        if (environment.isProduction()) {
            throw new IllegalStateException("SecureOne needs an encryption key kept outside the database. Mount a volume"
                    + " at " + file.getParent() + " (for example: docker run -v secureone-data:" + file.getParent()
                    + " ...) and SecureOne creates the key on the first start, or set SECUREONE_ENCRYPTION_KEY"
                    + " (base64:<32 random bytes>).");
        }
        this.value = null;
        this.origin = Origin.NONE;
    }

    /** The key ({@code base64:...}), or null when there is none outside the database. */
    public String value() {
        return value;
    }

    public Origin origin() {
        return origin;
    }

    /** True when the key lives in a file in the data volume (which first-run setup may show for backup). */
    public boolean inFile() {
        return origin == Origin.FILE || origin == Origin.GENERATED;
    }

    public Path file() {
        return file;
    }

    private static String read(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            String content = Files.readString(path, StandardCharsets.UTF_8).trim();
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the encryption key file " + path + ": " + e.getMessage(), e);
        }
    }

    /** Writes a new key file; false when another instance wrote one first. */
    private static boolean create(Path path) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String key = "base64:" + Base64.getEncoder().encodeToString(bytes);
        try {
            Files.writeString(path, key + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            try {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Not a POSIX file system: the volume's own permissions apply.
            }
            return true;
        } catch (FileAlreadyExistsException e) {
            return false;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write the encryption key file " + path + ": " + e.getMessage(), e);
        }
    }

    /** True when {@code dir} is itself a mount point (a volume), per /proc/self/mountinfo on Linux. */
    static boolean isMountPoint(Path dir) {
        Path mountinfo = Path.of("/proc/self/mountinfo");
        if (dir == null || !Files.isReadable(mountinfo)) {
            return false;
        }
        try {
            List<String> lines = Files.readAllLines(mountinfo, StandardCharsets.UTF_8);
            String wanted = dir.toString();
            for (String line : lines) {
                String[] fields = line.split(" ");
                // Field 5 is the mount point; spaces in it are written as \040.
                if (fields.length > 4 && fields[4].replace("\\040", " ").equals(wanted)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
