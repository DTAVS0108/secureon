package com.secureone.auth.logging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ApplicationLogQueryService {

    private final ApplicationLogRepository repository;

    public ApplicationLogQueryService(ApplicationLogRepository repository) {
        this.repository = repository;
    }

    public List<ApplicationLog> search(
            String sessionId,
            String requestId,
            String level,
            UUID applicationId,
            UUID tenantId,
            boolean includeUnscoped,
            Instant since,
            Instant until,
            String search,
            int limit) {
        ApplicationLogFilter filter = new ApplicationLogFilter(
                sessionId,
                requestId,
                level,
                applicationId,
                tenantId,
                includeUnscoped,
                since,
                until,
                search);
        return repository
                // Sort on the page request: findAll(spec, pageable) replaces the spec's ORDER BY with the pageable's.
                .findAll(
                        ApplicationLogSpecifications.from(filter),
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }
}
