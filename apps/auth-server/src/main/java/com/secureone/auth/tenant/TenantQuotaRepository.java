package com.secureone.auth.tenant;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantQuotaRepository extends JpaRepository<TenantQuota, UUID> {}
