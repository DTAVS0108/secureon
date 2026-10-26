-- Organizations (a tree of names inside a tenant) are removed: they had no members, no sign-in rules and no
-- claim in tokens, so nothing used them. Tenants are the customer-organisation boundary.
DROP TABLE IF EXISTS platform.organization CASCADE;
