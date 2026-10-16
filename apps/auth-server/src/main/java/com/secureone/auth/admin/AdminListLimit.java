package com.secureone.auth.admin;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Row cap for the console's history lists (audit log, sign-ins). These tables only grow, so an
 * unbounded list gets slower on every page load; the console shows the newest rows.
 */
public final class AdminListLimit {

    public static final int DEFAULT = 500;
    public static final int MAX = 2000;

    private AdminListLimit() {}

    /** First page of {@code requested} rows (default 500, clamped to 1..2000); the query supplies the order. */
    public static Pageable newest(Integer requested) {
        int size = requested == null ? DEFAULT : Math.max(1, Math.min(MAX, requested));
        return PageRequest.of(0, size);
    }
}
