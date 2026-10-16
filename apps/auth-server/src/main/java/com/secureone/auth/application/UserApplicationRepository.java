package com.secureone.auth.application;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserApplicationRepository extends JpaRepository<UserApplication, UserApplication.UserApplicationId> {

    List<UserApplication> findByApplicationId(UUID applicationId);

    List<UserApplication> findByUserId(UUID userId);

    boolean existsByUserIdAndApplicationId(UUID userId, UUID applicationId);

    // Bulk deletes: a derived delete loads each row and fails with a 500 (StaleStateException) when a
    // concurrent request removed it first, e.g. two overlapping "delete user" calls. These are no-ops then.
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM UserApplication ua WHERE ua.userId = :userId AND ua.applicationId = :applicationId")
    int deleteByUserIdAndApplicationId(@Param("userId") UUID userId, @Param("applicationId") UUID applicationId);

    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM UserApplication ua WHERE ua.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    @Query("SELECT ua.userId FROM UserApplication ua WHERE ua.applicationId = :applicationId")
    List<UUID> findUserIdsByApplicationId(@Param("applicationId") UUID applicationId);
}
