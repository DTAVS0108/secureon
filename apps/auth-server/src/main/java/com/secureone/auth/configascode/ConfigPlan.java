package com.secureone.auth.configascode;

import com.secureone.auth.configascode.TenantConfigService.Change;
import com.secureone.auth.configascode.TenantConfigService.CreatedSecret;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * What a configuration file changes, in run order, and the problems found while planning. Planning only
 * reads; every change is a step that runs at apply time. Order: first (create and update the main
 * objects), second (what refers to them: role includes, groups, settings, integrations), then deletions.
 */
class ConfigPlan {

    record Step(Change change, Runnable action) {}

    final boolean prune;
    final Function<String, String> environment;
    final List<String> errors = new ArrayList<>();
    final List<String> notes = new ArrayList<>();
    final List<CreatedSecret> secrets = new ArrayList<>();
    final List<Step> first = new ArrayList<>();
    final List<Step> second = new ArrayList<>();
    final List<Step> deletions = new ArrayList<>();

    ConfigPlan(boolean prune, Function<String, String> environment) {
        this.prune = prune;
        this.environment = environment;
    }

    List<Step> ordered() {
        List<Step> all = new ArrayList<>(first);
        all.addAll(second);
        all.addAll(deletions);
        return all;
    }

    List<Change> changes() {
        return ordered().stream().map(Step::change).distinct().toList();
    }

    /** Runs the steps in order and stops at the first failure (recorded in errors). Returns what was done. */
    List<Change> execute() {
        List<Change> done = new ArrayList<>();
        for (Step step : ordered()) {
            try {
                step.action().run();
                if (!done.contains(step.change())) {
                    done.add(step.change());
                }
            } catch (RuntimeException ex) {
                errors.add(String.format(
                        "Stopped at %s %s '%s': %s. The %d change(s) before it were applied; fix the file and apply it again.",
                        step.change().action(), step.change().kind(), step.change().target(),
                        ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName(), done.size()));
                break;
            }
        }
        return done;
    }

    /**
     * The value of an environment variable the file names for a credential, or null when it is not named.
     * A missing, too short or wrongly prefixed value is reported as a problem.
     */
    String secret(String variable, String owner, String requiredPrefix, int minLength) {
        if (variable == null || variable.isBlank()) {
            return null;
        }
        String value = environment.apply(variable.trim());
        if (value == null || value.isBlank()) {
            errors.add(owner + ": environment variable " + variable + " is not set on the server");
            return null;
        }
        if (requiredPrefix != null && !value.startsWith(requiredPrefix)) {
            errors.add(owner + ": the value of " + variable + " must start with " + requiredPrefix);
            return null;
        }
        if (value.length() < minLength) {
            errors.add(owner + ": the value of " + variable + " must be at least " + minLength + " characters");
            return null;
        }
        return value;
    }

    /** Credentials never go in the file: a literal is refused, with the field to use instead. */
    void refuseLiteralSecrets(String owner, Map<String, Object> values, Map<String, String> literalToEnvField) {
        if (values == null) {
            return;
        }
        literalToEnvField.forEach((literal, envField) -> {
            Object value = values.get(literal);
            if (value != null && !String.valueOf(value).isBlank()) {
                errors.add(owner + ": do not put '" + literal + "' in the file; put it in an environment variable on the"
                        + " server and name it with " + envField);
            }
        });
    }

    // ---------------------------------------------------------------- diff helpers

    static void diffText(List<String> diff, String field, String current, String wanted, boolean ignoreCase) {
        if (wanted == null) {
            return;
        }
        boolean same = ignoreCase ? wanted.equalsIgnoreCase(String.valueOf(current)) : wanted.equals(current);
        if (!same) {
            diff.add(field + ": " + quote(current) + " → " + quote(wanted));
        }
    }

    static void diffBoolean(List<String> diff, String field, boolean current, Boolean wanted) {
        if (wanted != null && wanted != current) {
            diff.add(field + ": " + current + " → " + wanted);
        }
    }

    static void diffSet(List<String> diff, String field, Collection<String> current, Collection<String> wanted) {
        if (wanted == null) {
            return;
        }
        Set<String> now = new TreeSet<>(current != null ? current : List.of());
        Set<String> want = new TreeSet<>(wanted);
        if (now.equals(want)) {
            return;
        }
        List<String> parts = new ArrayList<>();
        want.stream().filter(v -> !now.contains(v)).forEach(v -> parts.add("+" + v));
        now.stream().filter(v -> !want.contains(v)).forEach(v -> parts.add("−" + v));
        diff.add(field + ": " + String.join(", ", parts));
    }

    /** Whether everything the file sets is already stored (save paths may add defaults of their own). */
    static boolean contains(Object current, Object wanted) {
        if (wanted instanceof Map<?, ?> w) {
            if (!(current instanceof Map<?, ?> c)) {
                return false;
            }
            for (Map.Entry<?, ?> e : w.entrySet()) {
                if (!c.containsKey(e.getKey()) || !contains(c.get(e.getKey()), e.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if (wanted instanceof List<?> w) {
            if (!(current instanceof List<?> c) || c.size() != w.size()) {
                return false;
            }
            for (int i = 0; i < w.size(); i++) {
                if (!contains(c.get(i), w.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (wanted instanceof Number w && current instanceof Number c) {
            return w.doubleValue() == c.doubleValue();
        }
        return Objects.equals(wanted, current);
    }

    static String quote(String value) {
        return value == null ? "(none)" : "'" + value + "'";
    }
}
