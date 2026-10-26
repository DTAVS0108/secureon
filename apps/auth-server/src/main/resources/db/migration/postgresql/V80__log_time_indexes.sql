-- Platform-wide audit search, the overview, export and the retention job filter on time alone; the existing
-- indexes lead with tenant or application, so those queries scanned the whole table.
CREATE INDEX IF NOT EXISTS idx_audit_log_created ON audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_history_created ON login_history(created_at);
