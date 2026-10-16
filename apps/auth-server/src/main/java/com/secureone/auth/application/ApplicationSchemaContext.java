package com.secureone.auth.application;

import com.secureone.auth.logging.RequestContext;
import java.util.UUID;
import org.slf4j.MDC;

/** Thread-local application id for schema routing within a request/transaction. */
public final class ApplicationSchemaContext {

    private static final ThreadLocal<UUID> APPLICATION_ID = new ThreadLocal<>();

    private ApplicationSchemaContext() {}

    /**
     * The id is mirrored into the logging MDC so log lines written while serving this application are
     * tagged with it (the console's per-application log view filters on that tag). Inside an HTTP request
     * RequestContextFilter clears the MDC at the end; outside one, {@link #clear()} removes it so a
     * background job does not tag later lines with a stale id.
     */
    public static void setApplicationId(UUID applicationId) {
        if (applicationId == null) {
            APPLICATION_ID.remove();
        } else {
            APPLICATION_ID.set(applicationId);
            MDC.put(RequestContext.APPLICATION_ID, applicationId.toString());
        }
    }

    public static UUID getApplicationId() {
        return APPLICATION_ID.get();
    }

    public static void clear() {
        APPLICATION_ID.remove();
        if (MDC.get(RequestContext.REQUEST_ID) == null) {
            MDC.remove(RequestContext.APPLICATION_ID);
        }
    }
}
