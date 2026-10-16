package com.secureone.auth.session;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID> {

    @Query("SELECT l FROM LoginHistory l ORDER BY l.createdAt DESC")
    List<LoginHistory> findRecent(Pageable page);

    List<LoginHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable page);

    List<LoginHistory> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId, Pageable page);

    List<LoginHistory> findByApplicationIdAndTenantIdOrderByCreatedAtDesc(UUID applicationId, UUID tenantId, Pageable page);
}
