package com.secureone.auth.notify;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmailTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)\\}\\}");

    private EmailTemplateRenderer() {}

    /**
     * For HTML bodies: values are HTML-escaped, so a name such as {@code <a href=...>} chosen at sign-up shows as text
     * instead of becoming a link in an email sent under this domain.
     */
    public static String renderHtml(String template, Map<String, String> variables) {
        Map<String, String> escaped = new java.util.HashMap<>();
        variables.forEach((k, v) -> escaped.put(k, escapeHtml(v)));
        return render(template, escaped);
    }

    static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    public static String render(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.getOrDefault(key, "");
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
