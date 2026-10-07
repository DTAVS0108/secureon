package com.secureone.auth.notify;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);

    private final PlatformMailSenderProvider mailSenderProvider;

    public EmailDeliveryService(PlatformMailSenderProvider mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public boolean isConfigured(UUID applicationId) {
        return mailSenderProvider.getIfConfigured(applicationId).isPresent();
    }

    public boolean isPlatformConfigured() {
        return mailSenderProvider.getPlatformIfConfigured().isPresent();
    }

    public void sendPlatform(
            Map<String, Object> emailSettings,
            String to,
            List<String> cc,
            List<String> bcc,
            String subject,
            String text,
            String html) {
        JavaMailSender mailSender = mailSenderProvider
                .getPlatformIfConfigured()
                .orElseThrow(() -> new IllegalStateException(
                        "Platform SMTP is not configured. Open Platform settings → Notifications and save SMTP settings."));
        sendWithSender(mailSender, emailSettings, to, cc, bcc, subject, text, html, null);
    }

    public void send(
            UUID applicationId,
            Map<String, Object> emailSettings,
            String to,
            List<String> cc,
            List<String> bcc,
            String subject,
            String text,
            String html) {
        // The application's own SMTP server if it has one, otherwise the platform SMTP server.
        JavaMailSender mailSender = mailSenderProvider
                .getIfConfigured(applicationId)
                .or(mailSenderProvider::getPlatformIfConfigured)
                .orElseThrow(() -> new IllegalStateException(
                        "SMTP is not configured. Save SMTP settings under Platform settings or Application settings → Notifications."));
        sendWithSender(mailSender, emailSettings, to, cc, bcc, subject, text, html, applicationId);
    }

    private void sendWithSender(
            JavaMailSender mailSender,
            Map<String, Object> emailSettings,
            String to,
            List<String> cc,
            List<String> bcc,
            String subject,
            String text,
            String html,
            UUID applicationId) {
        String fromAddress = normalize(string(emailSettings, "fromAddress", "noreply@secureone.local"));
        String fromName = string(emailSettings, "fromName", "SecureOne");
        String replyTo = string(emailSettings, "replyTo", "");
        String primaryTo = normalize(to);

        List<String> ccList = excludePrimary(validAddresses(distinct(cc)), primaryTo);
        List<String> bccList = validAddresses(distinct(bcc));
        List<String> envelopeBcc = new ArrayList<>();
        List<String> senderCopies = new ArrayList<>();

        for (String address : bccList) {
            if (address.equals(primaryTo) || ccList.contains(address)) {
                continue;
            }
            if (address.equals(fromAddress)) {
                senderCopies.add(address);
            } else {
                envelopeBcc.add(address);
            }
        }

        try {
            MimeMessage message = buildMessage(mailSender, fromName, fromAddress, replyTo, primaryTo, ccList, envelopeBcc, subject, text, html);
            mailSender.send(message);
            if (applicationId != null) {
                log.debug(
                        "Email sent application={} to={} cc={} bcc={}",
                        applicationId,
                        primaryTo,
                        ccList,
                        envelopeBcc);
            } else {
                log.debug("Platform email sent to={} cc={} bcc={}", primaryTo, ccList, envelopeBcc);
            }

            for (String copyTo : senderCopies) {
                MimeMessage copy = buildMessage(
                        mailSender,
                        fromName,
                        fromAddress,
                        replyTo,
                        copyTo,
                        List.of(),
                        List.of(),
                        "[Copy] " + subject,
                        text,
                        html);
                mailSender.send(copy);
                if (applicationId != null) {
                    log.debug("Sender copy delivered application={} to={}", applicationId, copyTo);
                } else {
                    log.debug("Platform sender copy delivered to={}", copyTo);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to send email via SMTP: " + ex.getMessage(), ex);
        }
    }

    private static MimeMessage buildMessage(
            JavaMailSender mailSender,
            String fromName,
            String fromAddress,
            String replyTo,
            String to,
            List<String> cc,
            List<String> bcc,
            String subject,
            String text,
            String html)
            throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        boolean multipart = html != null && !html.isBlank();
        MimeMessageHelper helper = new MimeMessageHelper(message, multipart, "UTF-8");
        helper.setFrom(fromName + " <" + fromAddress + ">");
        helper.setTo(to);
        helper.setSubject(subject);
        if (!replyTo.isBlank()) {
            helper.setReplyTo(replyTo);
        }
        if (!cc.isEmpty()) {
            helper.setCc(cc.toArray(new String[0]));
        }
        if (!bcc.isEmpty()) {
            helper.setBcc(bcc.toArray(new String[0]));
        }
        if (multipart) {
            helper.setText(text != null ? text : "", html);
        } else {
            helper.setText(text != null ? text : "", false);
        }
        return message;
    }

    private static List<String> excludePrimary(List<String> emails, String primaryTo) {
        if (emails.isEmpty()) {
            return emails;
        }
        return emails.stream().filter(email -> !email.equals(primaryTo)).toList();
    }

    private static List<String> validAddresses(List<String> emails) {
        List<String> out = new ArrayList<>();
        for (String email : emails) {
            if (email == null || email.isBlank()) {
                continue;
            }
            String normalized = normalize(email);
            try {
                InternetAddress address = new InternetAddress(normalized, true);
                address.validate();
                out.add(address.getAddress().toLowerCase(Locale.ROOT));
            } catch (AddressException ex) {
                log.warn("Skipping invalid email address: {}", email);
            }
        }
        return out;
    }

    private static List<String> distinct(List<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String email : emails) {
            if (email != null && !email.isBlank()) {
                String normalized = normalize(email);
                if (seen.add(normalized)) {
                    out.add(normalized);
                }
            }
        }
        return out;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String string(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    }
}
