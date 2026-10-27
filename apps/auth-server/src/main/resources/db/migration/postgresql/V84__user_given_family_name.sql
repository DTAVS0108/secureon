-- Given and family name stored as entered (OIDC given_name / family_name, SCIM name.givenName / familyName).
-- Before this they were joined into display_name and split again on the first space when read. Existing rows
-- keep NULL: the console still shows its split of display_name for them, but tokens only carry stored names.
ALTER TABLE platform.user_account
    ADD COLUMN IF NOT EXISTS given_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS family_name VARCHAR(150);
