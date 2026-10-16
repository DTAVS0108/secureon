-- The startup catalog repair used to run without an application's schema, so it seeded each
-- isolated application's default roles and permissions into the shared platform tables as well.
-- Isolated applications keep their RBAC in their own schema; these platform copies were never read
-- for them (tokens, console and checks use the application's schema). Remove them. Applications
-- that still use the shared tables (schema_name IS NULL) are not touched.

DELETE FROM platform.rbac_group
WHERE application_id IN (SELECT id FROM platform.application WHERE schema_name IS NOT NULL);

DELETE FROM platform.role
WHERE application_id IN (SELECT id FROM platform.application WHERE schema_name IS NOT NULL);

DELETE FROM platform.permission
WHERE application_id IN (SELECT id FROM platform.application WHERE schema_name IS NOT NULL);
