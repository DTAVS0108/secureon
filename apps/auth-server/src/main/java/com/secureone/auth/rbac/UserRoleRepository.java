package com.secureone.auth.rbac;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    long countByRoleId(UUID roleId);

    List<UserRole> findByRoleIdOrderByGrantedAtDesc(UUID roleId);

    List<UserRole> findByUserId(UUID userId);

    /** Bulk delete: a no-op instead of a StaleStateException (500) when a concurrent request deleted the rows. */
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM UserRole x WHERE x.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    @Query("SELECT DISTINCT ur.userId FROM UserRole ur WHERE ur.userId IS NOT NULL AND ur.roleId IN :roleIds")
    List<UUID> findDistinctUserIdsByRoleIdIn(@Param("roleIds") Collection<UUID> roleIds);

    @Query(
            """
            SELECT DISTINCT r.applicationId FROM UserRole ur
            JOIN Role r ON r.id = ur.roleId
            JOIN UserAccount u ON u.id = ur.userId
            WHERE LOWER(u.email) = LOWER(:email)
            """)
    List<UUID> findDistinctApplicationIdsByUserEmail(@Param("email") String email);

    @Modifying
    @Query(
            """
            DELETE FROM UserRole ur
            WHERE ur.userId = :userId
              AND ur.roleId IN (
                  SELECT r.id FROM Role r WHERE r.applicationId = :applicationId
              )
            """)
    void deleteByUserIdAndApplicationId(@Param("userId") UUID userId, @Param("applicationId") UUID applicationId);
}
