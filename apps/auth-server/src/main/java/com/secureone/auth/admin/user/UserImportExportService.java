package com.secureone.auth.admin.user;

import com.secureone.auth.admin.ConflictException;
import com.secureone.auth.admin.ResourceNotFoundException;
import com.secureone.auth.admin.user.UserAdminDtos.UserCreateRequest;
import com.secureone.auth.application.Application;
import com.secureone.auth.application.ApplicationEffectiveSettingsService;
import com.secureone.auth.application.ApplicationRepository;
import com.secureone.auth.application.ApplicationSettingsService;
import com.secureone.auth.application.UserApplication;
import com.secureone.auth.application.UserApplicationRepository;
import com.secureone.auth.audit.AuditService;
import com.secureone.auth.user.UserAccount;
import com.secureone.auth.user.UserAccountRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class UserImportExportService {

    private final ApplicationRepository applications;
    private final ApplicationSettingsService settings;
    private final ApplicationEffectiveSettingsService effectiveSettings;
    private final UserAccountRepository users;
    private final UserApplicationRepository memberships;
    private final UserAdminService userAdmin;
    private final AuditService auditService;

    public UserImportExportService(
            ApplicationRepository applications,
            ApplicationSettingsService settings,
            ApplicationEffectiveSettingsService effectiveSettings,
            UserAccountRepository users,
            UserApplicationRepository memberships,
            UserAdminService userAdmin,
            AuditService auditService) {
        this.applications = applications;
        this.settings = settings;
        this.effectiveSettings = effectiveSettings;
        this.users = users;
        this.memberships = memberships;
        this.userAdmin = userAdmin;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public byte[] exportUsers(UUID applicationId, String format) {
        requireExportEnabled(applicationId);
        Application app = requireApplication(applicationId);
        List<UUID> userIds = memberships.findUserIdsByApplicationId(applicationId);
        StringBuilder csv = new StringBuilder();
        csv.append("email,username,firstName,lastName,status\n");
        for (UUID userId : userIds) {
            users.findById(userId).ifPresent(u -> csv.append(toCsvRow(u)));
        }
        auditService.record(
                app.getTenantId(), "admin", "user.export", "application", applicationId, format, true);
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public Map<String, Object> importFromFile(UUID applicationId, MultipartFile file, String source) {
        requireImportEnabled(applicationId);
        requireSourceEnabled(applicationId, source);
        Application app = requireApplication(applicationId);
        List<Map<String, String>> rows = parseDelimitedFile(file, "excel".equalsIgnoreCase(source));
        return importRows(applicationId, app, rows, source);
    }

    public Map<String, Object> importRowsJson(UUID applicationId, List<Map<String, String>> rows) {
        requireImportEnabled(applicationId);
        Application app = requireApplication(applicationId);
        return importRows(applicationId, app, rows, "api");
    }

    private Map<String, Object> importRows(
            UUID applicationId, Application app, List<Map<String, String>> rows, String source) {
        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String email = row.getOrDefault("email", "").trim().toLowerCase(Locale.ROOT);
            if (email.isBlank()) {
                skipped++;
                errors.add("Row " + (i + 1) + ": missing email");
                continue;
            }
            try {
                var existing = users.findByTenantIdAndEmail(app.getTenantId(), email);
                if (existing.isPresent()) {
                    grantIfNeeded(applicationId, existing.get().getId());
                    skipped++;
                    continue;
                }
                UserCreateRequest req =
                        new UserCreateRequest(
                                app.getTenantId(),
                                email,
                                row.getOrDefault("username", ""),
                                row.getOrDefault("firstName", ""),
                                row.getOrDefault("lastName", ""),
                                row.getOrDefault("status", "active"),
                                List.of(),
                                applicationId);
                userAdmin.create(req);
                created++;
            } catch (ConflictException e) {
                skipped++;
                errors.add("Row " + (i + 1) + ": " + e.getMessage());
            } catch (RuntimeException e) {
                skipped++;
                errors.add("Row " + (i + 1) + ": " + e.getMessage());
            }
        }
        auditService.record(
                app.getTenantId(),
                "admin",
                "user.import",
                "application",
                applicationId,
                source + ":" + created,
                true);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("errors", errors);
        result.put("total", rows.size());
        return result;
    }

    private void grantIfNeeded(UUID applicationId, UUID userId) {
        if (!memberships.existsByUserIdAndApplicationId(userId, applicationId)) {
            UserApplication link = new UserApplication();
            link.setUserId(userId);
            link.setApplicationId(applicationId);
            memberships.save(link);
        }
    }

    private List<Map<String, String>> parseDelimitedFile(MultipartFile file, boolean excelLabel) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        if (excelLabel && (name.endsWith(".xlsx") || name.endsWith(".xls"))) {
            throw new IllegalArgumentException(
                    "Binary Excel files are not parsed yet. Save the sheet as CSV (UTF-8) and import again.");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return rows;
            }
            List<String> headers = parseCsvLine(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String key = normalizeHeader(headers.get(i));
                    if (!key.isBlank() && i < values.size()) {
                        row.put(key, values.get(i).trim());
                    }
                }
                rows.add(row);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read file: " + e.getMessage());
        }
        return rows;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String normalizeHeader(String header) {
        String h = header.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        return switch (h) {
            case "e-mail", "mail" -> "email";
            case "firstname", "givenname", "given_name", "first_name" -> "firstName";
            case "lastname", "surname", "sn", "familyname", "family_name", "last_name" -> "lastName";
            case "user", "login" -> "username";
            default -> header.trim().isEmpty() ? "" : header.trim().substring(0, 1).toLowerCase(Locale.ROOT)
                    + header.trim().substring(1).replace(" ", "");
        };
    }

    private static String toCsvRow(UserAccount u) {
        NameParts parts = u.getGivenName() != null || u.getFamilyName() != null
                ? new NameParts(u.getGivenName() != null ? u.getGivenName() : "", u.getFamilyName() != null ? u.getFamilyName() : "")
                : splitDisplayName(u.getDisplayName());
        return String.join(
                ",",
                escape(u.getEmail()),
                escape(u.getUsername() != null ? u.getUsername() : ""),
                escape(parts.firstName()),
                escape(parts.lastName()),
                escape(u.getStatus().toLowerCase(Locale.ROOT)));
    }

    private static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static NameParts splitDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return new NameParts("", "");
        }
        String trimmed = displayName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new NameParts(trimmed, "");
        }
        return new NameParts(trimmed.substring(0, space), trimmed.substring(space + 1).trim());
    }

    private record NameParts(String firstName, String lastName) {}

    private void requireImportEnabled(UUID applicationId) {
        if (!Boolean.TRUE.equals(settings.getUserDirectory(applicationId).get("importEnabled"))) {
            throw new IllegalStateException("User import is disabled for this application. Enable it in Settings → User directory.");
        }
    }

    private void requireExportEnabled(UUID applicationId) {
        if (!Boolean.TRUE.equals(settings.getUserDirectory(applicationId).get("exportEnabled"))) {
            throw new IllegalStateException("User export is disabled for this application. Enable it in Settings → User directory.");
        }
    }

    private void requireSourceEnabled(UUID applicationId, String source) {
        Map<String, Object> config = settings.getUserDirectory(applicationId);
        @SuppressWarnings("unchecked")
        Map<String, Object> sources = (Map<String, Object>) config.getOrDefault("sources", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> src = (Map<String, Object>) sources.get(source);
        if (src == null || !Boolean.TRUE.equals(src.get("enabled"))) {
            throw new IllegalStateException("Import source '" + source + "' is disabled in User directory settings.");
        }
    }

    private Application requireApplication(UUID applicationId) {
        return applications
                .findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
    }

    private static String stringVal(Object o) {
        return o != null ? o.toString().trim() : "";
    }
}
