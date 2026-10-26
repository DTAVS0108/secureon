package com.secureone.auth.audit;

/**
 * Published after an audit entry is saved. Listeners turn it into tenant webhooks and send it to the platform's
 * log streams; they run after the transaction commits, so a rolled-back change sends nothing.
 */
public record AuditRecordedEvent(AuditLog log) {}
