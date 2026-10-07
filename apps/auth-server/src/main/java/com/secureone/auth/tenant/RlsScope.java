package com.secureone.auth.tenant;

import java.util.function.Supplier;

/**
 * Marks work that may legitimately read every tenant's rows (authentication lookups, scheduled
 * jobs, platform operator tools). Transactions started inside {@link #asPlatform} set
 * {@code app.rls_bypass = 'on'}; everything else is limited to {@link TenantContext}'s tenant once
 * {@code secureone.rls.enforce} is on.
 */
public final class RlsScope {

    private static final ThreadLocal<Integer> PLATFORM_DEPTH = ThreadLocal.withInitial(() -> 0);

    private RlsScope() {}

    public static <T> T asPlatform(Supplier<T> work) {
        PLATFORM_DEPTH.set(PLATFORM_DEPTH.get() + 1);
        try {
            return work.get();
        } finally {
            int depth = PLATFORM_DEPTH.get() - 1;
            if (depth == 0) {
                PLATFORM_DEPTH.remove();
            } else {
                PLATFORM_DEPTH.set(depth);
            }
        }
    }

    public static void runAsPlatform(Runnable work) {
        asPlatform(() -> {
            work.run();
            return null;
        });
    }

    public static boolean isPlatform() {
        return PLATFORM_DEPTH.get() > 0;
    }
}
