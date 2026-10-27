package com.secureone.auth.saml;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureone.auth.tenant.RlsScope;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** SAML service providers (applications that sign users in with SAML), each under one of the tenant's applications. */
@Service
public class SamlServiceProviderService {

    /** Name ID formats an SP can be given, with their SAML URIs. */
    public static final Map<String, String> NAME_ID_FORMATS = Map.of(
            "email", "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress",
            "persistent", "urn:oasis:names:tc:SAML:2.0:nameid-format:persistent",
            "unspecified", "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified");

    /** Where an attribute's value comes from (token claim sources, plus the app's roles and permissions). */
    public static final Set<String> SOURCES = Set.of(
            "email", "email_verified", "phone", "display_name", "given_name", "family_name", "username", "user_id",
            "tenant_slug", "roles", "permissions", "attribute", "static");

    /** What an SP gets when it lists no attributes. */
    public static final List<Attribute> DEFAULT_ATTRIBUTES = List.of(
            new Attribute("email", "email", null),
            new Attribute("given_name", "given_name", null),
            new Attribute("family_name", "family_name", null),
            new Attribute("name", "display_name", null),
            new Attribute("roles", "roles", null));

    static final int MAX_ATTRIBUTES = 30;
    private static final Pattern ATTRIBUTE_NAME = Pattern.compile("^[A-Za-z0-9_.:/\\-]{1,200}$");
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Attribute(String name, String source, String value) {}

    public record ServiceProvider(
            UUID id, UUID tenantId, UUID applicationId, String name, String entityId, String acsUrl, String sloUrl,
            String nameIdFormat, boolean signResponse, boolean signAssertion, String spCertificate,
            boolean requireSignedRequests, List<Attribute> attributes, int assertionLifetimeSeconds, String status) {

        public boolean active() {
            return "ACTIVE".equals(status);
        }

        /** The attributes asserted: the configured ones, or {@link #DEFAULT_ATTRIBUTES}. */
        public List<Attribute> effectiveAttributes() {
            return attributes.isEmpty() ? DEFAULT_ATTRIBUTES : attributes;
        }
    }

    private final JdbcTemplate jdbc;

    public SamlServiceProviderService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ServiceProvider> list(UUID applicationId) {
        return RlsScope.asPlatform(() -> jdbc.query(
                "SELECT * FROM platform.saml_service_provider WHERE application_id = ? ORDER BY lower(name)", this::row, applicationId));
    }

    public Optional<ServiceProvider> find(UUID applicationId, UUID id) {
        return list(applicationId).stream().filter(sp -> sp.id().equals(id)).findFirst();
    }

    public Optional<ServiceProvider> byId(UUID id) {
        return RlsScope.asPlatform(() -> jdbc.query(
                "SELECT * FROM platform.saml_service_provider WHERE id = ?", this::row, id).stream().findFirst());
    }

    /** The tenant's service provider with this entity ID (its AuthnRequest Issuer). */
    public Optional<ServiceProvider> byEntityId(UUID tenantId, String entityId) {
        if (entityId == null) {
            return Optional.empty();
        }
        return RlsScope.asPlatform(() -> jdbc.query(
                "SELECT * FROM platform.saml_service_provider WHERE tenant_id = ? AND lower(entity_id) = lower(?)",
                this::row, tenantId, entityId.trim()).stream().findFirst());
    }

    public ServiceProvider create(UUID tenantId, UUID applicationId, Map<String, Object> body) {
        ServiceProvider sp = validated(null, tenantId, applicationId, body, null);
        requireUniqueEntityId(tenantId, sp.entityId(), null);
        RlsScope.runAsPlatform(() -> jdbc.update(
                "INSERT INTO platform.saml_service_provider (id, tenant_id, application_id, name, entity_id, acs_url, slo_url, "
                        + "name_id_format, sign_response, sign_assertion, sp_certificate, require_signed_requests, attributes, "
                        + "assertion_lifetime_seconds, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)",
                sp.id(), tenantId, applicationId, sp.name(), sp.entityId(), sp.acsUrl(), sp.sloUrl(), sp.nameIdFormat(),
                sp.signResponse(), sp.signAssertion(), sp.spCertificate(), sp.requireSignedRequests(),
                json(sp.attributes()), sp.assertionLifetimeSeconds(), sp.status()));
        return byId(sp.id()).orElseThrow();
    }

    public ServiceProvider update(UUID applicationId, UUID id, Map<String, Object> body) {
        ServiceProvider current = find(applicationId, id)
                .orElseThrow(() -> new com.secureone.auth.admin.ResourceNotFoundException("SAML app not found: " + id));
        ServiceProvider sp = validated(id, current.tenantId(), applicationId, body, current);
        requireUniqueEntityId(current.tenantId(), sp.entityId(), id);
        RlsScope.runAsPlatform(() -> jdbc.update(
                "UPDATE platform.saml_service_provider SET name = ?, entity_id = ?, acs_url = ?, slo_url = ?, name_id_format = ?, "
                        + "sign_response = ?, sign_assertion = ?, sp_certificate = ?, require_signed_requests = ?, attributes = ?::jsonb, "
                        + "assertion_lifetime_seconds = ?, status = ?, updated_at = now() WHERE id = ?",
                sp.name(), sp.entityId(), sp.acsUrl(), sp.sloUrl(), sp.nameIdFormat(), sp.signResponse(), sp.signAssertion(),
                sp.spCertificate(), sp.requireSignedRequests(), json(sp.attributes()), sp.assertionLifetimeSeconds(),
                sp.status(), id));
        return byId(id).orElseThrow();
    }

    public ServiceProvider delete(UUID applicationId, UUID id) {
        ServiceProvider sp = find(applicationId, id)
                .orElseThrow(() -> new com.secureone.auth.admin.ResourceNotFoundException("SAML app not found: " + id));
        RlsScope.runAsPlatform(() -> jdbc.update("DELETE FROM platform.saml_service_provider WHERE id = ?", id));
        return sp;
    }

    private void requireUniqueEntityId(UUID tenantId, String entityId, UUID self) {
        byEntityId(tenantId, entityId).filter(other -> !other.id().equals(self)).ifPresent(other -> {
            throw new com.secureone.auth.admin.ConflictException(
                    "Another SAML app of this organization has the entity ID " + entityId + ".");
        });
    }

    /** The SP as the body describes it; on an update a field left out keeps its value. */
    private ServiceProvider validated(UUID id, UUID tenantId, UUID applicationId, Map<String, Object> body, ServiceProvider current) {
        String name = text(body, "name", current == null ? null : current.name());
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("name is required (at most 200 characters).");
        }
        String entityId = text(body, "entityId", current == null ? null : current.entityId());
        if (entityId == null || entityId.isBlank() || entityId.length() > 1024 || entityId.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("entityId is required: the SP's entity ID (its metadata entityID), without spaces.");
        }
        String acs = url("acsUrl", text(body, "acsUrl", current == null ? null : current.acsUrl()), true);
        String slo = url("sloUrl", text(body, "sloUrl", current == null ? null : current.sloUrl()), false);
        String format = text(body, "nameIdFormat", current == null ? "email" : current.nameIdFormat());
        format = format == null ? "email" : format.trim().toLowerCase(Locale.ROOT);
        if (!NAME_ID_FORMATS.containsKey(format)) {
            throw new IllegalArgumentException("nameIdFormat must be email, persistent or unspecified.");
        }
        String certificate = text(body, "spCertificate", current == null ? null : current.spCertificate());
        if (certificate != null && !certificate.isBlank()) {
            certificate = SamlIdpCredentialService.pemOf(SamlIdpCredentialService.parseCertificate(certificate));
        } else {
            certificate = null;
        }
        boolean requireSigned = bool(body, "requireSignedRequests", current != null && current.requireSignedRequests());
        if (requireSigned && certificate == null) {
            throw new IllegalArgumentException("Signed requests need the SP's signing certificate (spCertificate).");
        }
        boolean signResponse = bool(body, "signResponse", current == null || current.signResponse());
        boolean signAssertion = bool(body, "signAssertion", current == null || current.signAssertion());
        if (!signResponse && !signAssertion) {
            throw new IllegalArgumentException("Sign the response, the assertion, or both: an unsigned assertion can be forged.");
        }
        int lifetime = current == null ? 300 : current.assertionLifetimeSeconds();
        if (body.get("assertionLifetimeSeconds") instanceof Number n) {
            lifetime = n.intValue();
        }
        if (lifetime < 60 || lifetime > 3600) {
            throw new IllegalArgumentException("assertionLifetimeSeconds must be 60 to 3600.");
        }
        List<Attribute> attributes = body.containsKey("attributes")
                ? attributes(body.get("attributes"))
                : current == null ? List.of() : current.attributes();
        String status = text(body, "status", current == null ? "ACTIVE" : current.status());
        status = status == null ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
        if (!status.equals("ACTIVE") && !status.equals("DISABLED")) {
            throw new IllegalArgumentException("status must be active or disabled.");
        }
        return new ServiceProvider(id != null ? id : UUID.randomUUID(), tenantId, applicationId, name.trim(), entityId.trim(),
                acs, slo, format, signResponse, signAssertion, certificate, requireSigned, attributes, lifetime, status);
    }

    static List<Attribute> attributes(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("attributes must be a list of {name, source, value}.");
        }
        if (list.size() > MAX_ATTRIBUTES) {
            throw new IllegalArgumentException("At most " + MAX_ATTRIBUTES + " attributes.");
        }
        List<Attribute> out = new ArrayList<>();
        Set<String> names = new java.util.HashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("attributes must be a list of {name, source, value}.");
            }
            String name = map.get("name") == null ? "" : String.valueOf(map.get("name")).trim();
            String source = map.get("source") == null ? "" : String.valueOf(map.get("source")).trim().toLowerCase(Locale.ROOT);
            String value = map.get("value") == null ? null : String.valueOf(map.get("value")).trim();
            if (!ATTRIBUTE_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Attribute names are 1 to 200 letters, digits or _ . : / - (\"" + name + "\").");
            }
            if (!names.add(name)) {
                throw new IllegalArgumentException("Attribute \"" + name + "\" is listed twice.");
            }
            if (!SOURCES.contains(source)) {
                throw new IllegalArgumentException("Attribute \"" + name + "\": source must be one of " + new java.util.TreeSet<>(SOURCES) + ".");
            }
            if (("attribute".equals(source) || "static".equals(source)) && (value == null || value.isEmpty())) {
                throw new IllegalArgumentException("Attribute \"" + name + "\" needs a " + ("static".equals(source) ? "value" : "user attribute name") + ".");
            }
            if ("attribute".equals(source) && com.secureone.auth.application.TokenClaimMappings.isInternalAttribute(value)) {
                throw new IllegalArgumentException("Attribute \"" + name + "\": " + value + " is kept by SecureOne for itself.");
            }
            out.add(new Attribute(name, source, "attribute".equals(source) || "static".equals(source) ? value : null));
        }
        return out;
    }

    /** https:// (http:// only for localhost), no fragment; blank is none unless required. */
    static String url(String field, String raw, boolean required) {
        if (raw == null || raw.isBlank()) {
            if (required) {
                throw new IllegalArgumentException(field + " is required.");
            }
            return null;
        }
        URI uri;
        try {
            uri = new URI(raw.trim());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException(field + " is not a valid address.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]");
        if (!("https".equals(scheme) || ("http".equals(scheme) && local)) || host.isEmpty() || uri.getFragment() != null
                || raw.trim().length() > 1024) {
            throw new IllegalArgumentException(field + " must be an https:// address (http:// only for localhost), without #.");
        }
        return uri.toString();
    }

    public Map<String, Object> view(ServiceProvider sp) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", sp.id());
        out.put("applicationId", sp.applicationId());
        out.put("name", sp.name());
        out.put("entityId", sp.entityId());
        out.put("acsUrl", sp.acsUrl());
        out.put("sloUrl", sp.sloUrl());
        out.put("nameIdFormat", sp.nameIdFormat());
        out.put("signResponse", sp.signResponse());
        out.put("signAssertion", sp.signAssertion());
        out.put("spCertificate", sp.spCertificate());
        out.put("requireSignedRequests", sp.requireSignedRequests());
        out.put("attributes", sp.attributes());
        out.put("effectiveAttributes", sp.effectiveAttributes());
        out.put("assertionLifetimeSeconds", sp.assertionLifetimeSeconds());
        out.put("status", sp.status().toLowerCase(Locale.ROOT));
        return out;
    }

    private ServiceProvider row(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        List<Attribute> attributes;
        try {
            attributes = JSON.readValue(rs.getString("attributes"), new TypeReference<List<Attribute>>() {});
        } catch (Exception e) {
            attributes = List.of();
        }
        return new ServiceProvider(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getObject("application_id", UUID.class),
                rs.getString("name"), rs.getString("entity_id"), rs.getString("acs_url"), rs.getString("slo_url"),
                rs.getString("name_id_format"), rs.getBoolean("sign_response"), rs.getBoolean("sign_assertion"),
                rs.getString("sp_certificate"), rs.getBoolean("require_signed_requests"), attributes,
                rs.getInt("assertion_lifetime_seconds"), rs.getString("status"));
    }

    private static String json(List<Attribute> attributes) {
        try {
            return JSON.writeValueAsString(attributes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(Map<String, Object> body, String key, String fallback) {
        if (!body.containsKey(key)) {
            return fallback;
        }
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean bool(Map<String, Object> body, String key, boolean fallback) {
        Object value = body.get(key);
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
