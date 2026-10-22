package com.secureone.auth.twostep;

import com.secureone.auth.notify.EmailNotificationService;
import com.secureone.auth.tenant.RlsScope;
import com.secureone.auth.user.UserAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Emails users who must set up two-step verification before a deadline, on the platform's reminder days
 * (default 7 and 1 days before). Each reminder is sent once.
 */
@Component
public class TwoStepReminderJob {

    private static final Logger log = LoggerFactory.getLogger(TwoStepReminderJob.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneOffset.UTC);

    private final TwoStepStateStore states;
    private final TwoStepPolicyResolver policies;
    private final TwoStepService twoStep;
    private final UserAccountRepository users;
    private final EmailNotificationService mail;
    private final String publicBaseUrl;

    public TwoStepReminderJob(
            TwoStepStateStore states,
            TwoStepPolicyResolver policies,
            TwoStepService twoStep,
            UserAccountRepository users,
            EmailNotificationService mail,
            @Value("${secureone.public-base-url:http://localhost:9000}") String publicBaseUrl) {
        this.states = states;
        this.policies = policies;
        this.twoStep = twoStep;
        this.users = users;
        this.mail = mail;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    @Scheduled(cron = "${secureone.two-step.reminder-cron:0 0 8 * * *}")
    public void run() {
        RlsScope.runAsPlatform(this::sendDue);
    }

    /** Sends the reminders that are due now; returns how many were sent. */
    @SuppressWarnings("unchecked")
    public int sendDue() {
        Map<String, Object> platform = policies.platform();
        List<Integer> days = platform.get("reminderDays") instanceof List<?> l ? (List<Integer>) l : List.of(7, 1);
        if (days.isEmpty()) {
            return 0;
        }
        int sent = 0;
        int horizon = days.stream().mapToInt(Integer::intValue).max().orElse(7);
        for (TwoStepStateStore.State s : states.deadlinesWithin(horizon)) {
            long daysLeft = Math.max(0, Duration.between(Instant.now(), s.deadline()).toHours() + 23) / 24;
            List<String> already = new ArrayList<>(Arrays.asList(s.remindersSent().split(",")));
            Integer due = days.stream().filter(d -> daysLeft <= d && !already.contains(String.valueOf(d)))
                    .min(Integer::compareTo).orElse(null);
            if (due == null || !twoStep.methods(s.userId()).isEmpty()) {
                continue;
            }
            var user = users.findById(s.userId()).orElse(null);
            if (user == null || s.exempt(Instant.now())) {
                continue;
            }
            try {
                mail.sendTwoStepReminder(null, user, DATE.format(s.deadline()), publicBaseUrl + "/login.html");
                sent++;
            } catch (RuntimeException ex) {
                log.warn("Two-step reminder to {} failed: {}", user.getEmail(), ex.getMessage());
            }
            // Every reminder day already passed counts as sent, so a late run sends one email, not several.
            days.stream().filter(d -> daysLeft <= d).forEach(d -> already.add(String.valueOf(d)));
            states.save(new TwoStepStateStore.State(s.userId(), s.firstPromptedAt(), s.nextPromptAt(), s.skipsUsed(),
                    s.deadline(), s.optedOut(), s.exemptUntil(), s.exemptReason(), s.exemptedBy(), s.extendedBy(),
                    String.join(",", already.stream().filter(x -> !x.isBlank()).distinct().toList())));
        }
        return sent;
    }
}
