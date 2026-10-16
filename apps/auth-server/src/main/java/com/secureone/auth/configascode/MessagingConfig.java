package com.secureone.auth.configascode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.secureone.auth.configascode.ConfigPlan.Step;
import com.secureone.auth.configascode.TenantConfigService.Change;
import com.secureone.auth.notify.sms.SmsResolvedCredentials;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * SMTP server and SMS provider in configuration files: an application's {@code settings.smtp} /
 * {@code settings.sms}, and the platform file's defaults. Passwords, tokens and keys come from
 * environment variables; the file names them (passwordEnv, authTokenEnv, secretAccessKeyEnv, apiKeyEnv).
 */
final class MessagingConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private MessagingConfig() {}

    // ---------------------------------------------------------------- reading the file

    /** settings.smtp as a record, or null with the problem recorded. */
    static TenantConfig.Smtp smtp(ConfigPlan plan, String owner, Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            plan.errors.add(owner + " must be a mapping (key: value)");
            return null;
        }
        if (literal(plan, owner, map, "password", "passwordEnv")) {
            return null;
        }
        return convert(plan, owner, map, TenantConfig.Smtp.class);
    }

    static TenantConfig.Sms sms(ConfigPlan plan, String owner, Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            plan.errors.add(owner + " must be a mapping (key: value)");
            return null;
        }
        boolean literal = literal(plan, owner + ".twilio", map.get("twilio"), "authToken", "authTokenEnv")
                | literal(plan, owner + ".aws_sns", map.get("aws_sns"), "secretAccessKey", "secretAccessKeyEnv")
                | literal(plan, owner + ".msg91", map.get("msg91"), "apiKey", "apiKeyEnv");
        return literal ? null : convert(plan, owner, map, TenantConfig.Sms.class);
    }

    private static boolean literal(ConfigPlan plan, String owner, Object raw, String field, String envField) {
        if (raw instanceof Map<?, ?> map && map.get(field) != null && !String.valueOf(map.get(field)).isBlank()) {
            plan.errors.add(owner + ": do not put '" + field + "' in the file; put it in an environment variable on the"
                    + " server and name it with " + envField);
            return true;
        }
        return false;
    }

    private static <T> T convert(ConfigPlan plan, String owner, Map<?, ?> map, Class<T> type) {
        try {
            return MAPPER.convertValue(map, type);
        } catch (IllegalArgumentException ex) {
            if (ex.getCause() instanceof UnrecognizedPropertyException u) {
                plan.errors.add(owner + ": unknown field '" + u.getPropertyName() + "'. Allowed: "
                        + u.getKnownPropertyIds().stream().map(String::valueOf).sorted().collect(Collectors.joining(", ")));
            } else {
                plan.errors.add(owner + ": " + ex.getMessage());
            }
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toMap(Object record) {
        return record == null ? null : MAPPER.convertValue(record, Map.class);
    }

    // ---------------------------------------------------------------- export (from a public view)

    static TenantConfig.Smtp smtpFromView(Map<String, Object> view) {
        if (view == null || text(view.get("host")) == null) {
            return null;
        }
        Object port = view.get("port");
        return new TenantConfig.Smtp(
                text(view.get("host")),
                port instanceof Number n ? n.intValue() : port != null ? Integer.valueOf(port.toString()) : null,
                text(view.get("security")),
                text(view.get("username")),
                view.get("authEnabled") instanceof Boolean b ? b : null,
                null);
    }

    @SuppressWarnings("unchecked")
    static TenantConfig.Sms smsFromView(Map<String, Object> view) {
        if (view == null) {
            return null;
        }
        Map<String, Object> twilio = view.get("twilio") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> aws = view.get("aws_sns") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> msg91 = view.get("msg91") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        TenantConfig.Twilio t = new TenantConfig.Twilio(text(twilio.get("accountSid")), text(twilio.get("fromNumber")), null);
        TenantConfig.AwsSns a = new TenantConfig.AwsSns(
                text(aws.get("region")), text(aws.get("accessKeyId")), text(aws.get("senderId")), null);
        TenantConfig.Msg91 g = new TenantConfig.Msg91(text(msg91.get("senderId")), text(msg91.get("templateId")), null);
        return new TenantConfig.Sms(
                Boolean.TRUE.equals(view.get("enabled")),
                text(view.get("provider")),
                t.accountSid() == null && t.fromNumber() == null ? null : t,
                a.region() == null && a.accessKeyId() == null && a.senderId() == null ? null : a,
                g.senderId() == null && g.templateId() == null ? null : g);
    }

    // ---------------------------------------------------------------- plan

    /**
     * @param current the stored settings (public view), or null when there is no override yet
     * @param save runs at apply time with the body for the settings service
     */
    static void planSmtp(ConfigPlan plan, String target, TenantConfig.Smtp wanted, Map<String, Object> current,
            Predicate<String> passwordMatches, Consumer<Map<String, Object>> save) {
        String password = plan.secret(wanted.passwordEnv(), target, null, 1);
        Map<String, Object> body = new LinkedHashMap<>();
        put(body, "host", wanted.host());
        put(body, "port", wanted.port());
        put(body, "security", wanted.security());
        put(body, "username", wanted.username());
        put(body, "authEnabled", wanted.authEnabled());
        List<String> diff = new ArrayList<>();
        if (current == null) {
            body.forEach((k, v) -> diff.add(k + ": " + v));
            if (password != null) {
                diff.add("password: from " + wanted.passwordEnv());
                body.put("password", password);
            }
        } else {
            ConfigPlan.diffText(diff, "host", text(current.get("host")), wanted.host(), true);
            ConfigPlan.diffText(diff, "port", text(current.get("port")), wanted.port() != null ? wanted.port().toString() : null, false);
            ConfigPlan.diffText(diff, "security", text(current.get("security")), wanted.security(), true);
            ConfigPlan.diffText(diff, "username", text(current.get("username")), wanted.username(), false);
            ConfigPlan.diffBoolean(diff, "authEnabled", Boolean.TRUE.equals(current.get("authEnabled")), wanted.authEnabled());
            if (password != null && !passwordMatches.test(password)) {
                diff.add("password: set from " + wanted.passwordEnv());
                body.put("password", password);
            }
            if (diff.isEmpty()) {
                return;
            }
        }
        plan.second.add(new Step(new Change(current == null ? "create" : "update", "smtp", target, diff), () -> save.accept(body)));
    }

    @SuppressWarnings("unchecked")
    static void planSms(ConfigPlan plan, String target, TenantConfig.Sms wanted, Map<String, Object> current,
            Supplier<SmsResolvedCredentials> stored, Consumer<Map<String, Object>> save) {
        TenantConfig.Twilio twilio = wanted.twilio();
        TenantConfig.AwsSns aws = wanted.awsSns();
        TenantConfig.Msg91 msg91 = wanted.msg91();
        String authToken = twilio != null ? plan.secret(twilio.authTokenEnv(), target + ".twilio", null, 1) : null;
        String awsSecret = aws != null ? plan.secret(aws.secretAccessKeyEnv(), target + ".aws_sns", null, 1) : null;
        String msg91Key = msg91 != null ? plan.secret(msg91.apiKeyEnv(), target + ".msg91", null, 1) : null;

        Map<String, Object> body = new LinkedHashMap<>();
        put(body, "enabled", wanted.enabled());
        put(body, "provider", wanted.provider());
        List<String> diff = new ArrayList<>();
        SmsResolvedCredentials creds = current != null ? stored.get() : null;
        Map<String, Object> curTwilio = current != null && current.get("twilio") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> curAws = current != null && current.get("aws_sns") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> curMsg = current != null && current.get("msg91") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        if (current != null) {
            ConfigPlan.diffBoolean(diff, "enabled", Boolean.TRUE.equals(current.get("enabled")), wanted.enabled());
            ConfigPlan.diffText(diff, "provider", text(current.get("provider")), wanted.provider(), true);
        }
        if (twilio != null) {
            Map<String, Object> t = new LinkedHashMap<>();
            put(t, "accountSid", twilio.accountSid());
            put(t, "fromNumber", twilio.fromNumber());
            ConfigPlan.diffText(diff, "twilio.accountSid", text(curTwilio.get("accountSid")), twilio.accountSid(), false);
            ConfigPlan.diffText(diff, "twilio.fromNumber", text(curTwilio.get("fromNumber")), twilio.fromNumber(), false);
            if (authToken != null && (creds == null || !authToken.equals(creds.twilioAuthToken()))) {
                t.put("authToken", authToken);
                diff.add("twilio.authToken: set from " + twilio.authTokenEnv());
            }
            body.put("twilio", t);
        }
        if (aws != null) {
            Map<String, Object> a = new LinkedHashMap<>();
            put(a, "region", aws.region());
            put(a, "accessKeyId", aws.accessKeyId());
            put(a, "senderId", aws.senderId());
            ConfigPlan.diffText(diff, "aws_sns.region", text(curAws.get("region")), aws.region(), false);
            ConfigPlan.diffText(diff, "aws_sns.accessKeyId", text(curAws.get("accessKeyId")), aws.accessKeyId(), false);
            ConfigPlan.diffText(diff, "aws_sns.senderId", text(curAws.get("senderId")), aws.senderId(), false);
            if (awsSecret != null && (creds == null || !awsSecret.equals(creds.awsSecretAccessKey()))) {
                a.put("secretAccessKey", awsSecret);
                diff.add("aws_sns.secretAccessKey: set from " + aws.secretAccessKeyEnv());
            }
            body.put("aws_sns", a);
        }
        if (msg91 != null) {
            Map<String, Object> g = new LinkedHashMap<>();
            put(g, "senderId", msg91.senderId());
            put(g, "templateId", msg91.templateId());
            ConfigPlan.diffText(diff, "msg91.senderId", text(curMsg.get("senderId")), msg91.senderId(), false);
            ConfigPlan.diffText(diff, "msg91.templateId", text(curMsg.get("templateId")), msg91.templateId(), false);
            if (msg91Key != null && (creds == null || !msg91Key.equals(creds.msg91ApiKey()))) {
                g.put("apiKey", msg91Key);
                diff.add("msg91.apiKey: set from " + msg91.apiKeyEnv());
            }
            body.put("msg91", g);
        }
        if (current == null) {
            diff.add(0, "provider: " + Objects.requireNonNullElse(wanted.provider(), "log"));
        } else if (diff.isEmpty()) {
            return;
        }
        plan.second.add(new Step(new Change(current == null ? "create" : "update", "sms", target, diff), () -> save.accept(body)));
    }

    private static void put(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
