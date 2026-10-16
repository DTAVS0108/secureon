package com.secureone.auth.logging;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class ApplicationLogSpecifications {

    private ApplicationLogSpecifications() {}

    static Specification<ApplicationLog> from(ApplicationLogFilter filter) {
        return from(filter, true);
    }

    static Specification<ApplicationLog> from(ApplicationLogFilter filter, boolean sort) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.sessionId() != null) {
                predicates.add(cb.equal(root.get("sessionId"), filter.sessionId()));
            }
            if (filter.requestId() != null) {
                predicates.add(cb.equal(root.get("requestId"), filter.requestId()));
            }
            if (filter.level() != null) {
                predicates.add(cb.equal(root.get("level"), filter.level()));
            }
            if (filter.applicationId() != null) {
                if (filter.includeUnscoped()) {
                    predicates.add(cb.or(
                            cb.equal(root.get("applicationId"), filter.applicationId()),
                            cb.isNull(root.get("applicationId"))));
                } else {
                    predicates.add(cb.equal(root.get("applicationId"), filter.applicationId()));
                }
            }
            // An application belongs to one tenant, so with an app filter the tenant one adds nothing and
            // would only drop that app's lines logged outside a tenant context (platform operators).
            if (filter.tenantId() != null && filter.applicationId() == null) {
                if (filter.includeUnscoped()) {
                    predicates.add(cb.or(
                            cb.equal(root.get("tenantId"), filter.tenantId()),
                            cb.isNull(root.get("tenantId"))));
                } else {
                    predicates.add(cb.equal(root.get("tenantId"), filter.tenantId()));
                }
            }
            if (filter.since() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.since()));
            }
            if (filter.until() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.until()));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = "%" + filter.search().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("message")), pattern),
                        cb.like(cb.lower(root.get("logger")), pattern)));
            }
            if (sort && query != null) {
                query.orderBy(cb.desc(root.get("createdAt")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
