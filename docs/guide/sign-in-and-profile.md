---
title: Sign-in, setup and My profile
summary: First-run setup, sign-in, authenticator, profile picture
order: 1
audience: public
---

# Sign-in, setup and My profile

[User guide](index.md) › Sign-in and profile

## First-run setup (`/admin/setup`)

A new, empty SecureOne has no administrator. You create the first platform operator in one of two ways:

- **From the container settings** (no screen): set `SECUREONE_BOOTSTRAP_ADMIN_EMAIL` and `SECUREONE_BOOTSTRAP_ADMIN_PASSWORD` (optionally `…_FIRST_NAME`, `…_LAST_NAME`) when you create the container. The account is created at the first start and the setup page is closed. See [Deployment: image + env & enterprise](../install/deployment.md).
- **On the setup page:** while no operator exists, the sign-in page shows *SecureOne is not set up yet: no platform operator exists.* **Set it up** *with the code from the server log.* The link opens the setup form.

### Setup form fields

| Field | What to enter |
|-------|---------------|
| **Setup code** | The one-time code printed in the server log at startup (format `ABCD-EFGH-JKLM`). It proves you have access to the server. |
| **First name**, **Last name** | Your name, shown in the console and the audit log. |
| **Work email** | Your sign-in email. |
| **Password** | At least 12 characters, with upper case, a number and a symbol. The checklist under the field shows each rule as it is met. |
| **Confirm password** | The same password again. |

The note under the form explains: *This account administers the whole platform. After you sign in you will add an authenticator app (required for operators).*

After submitting, the sign-in page opens with *Your operator account is ready. Sign in under Platform operator; you will add an authenticator app next.*

## Sign-in page (`/admin/login`)

The page has two tabs: **Organization** and **Platform operator**.

### Organization tab

For tenant operators (Tenant Super Admin, Tenant Admin, Application Admin).

| Field | What to enter |
|-------|---------------|
| **Tenant slug** | Your organization's short name, for example `tenant-a` (placeholder `your-organization`). Your platform operator gives it to you. |
| **Email** | Your work email. |
| **Password** | Your password. **Show** / **Hide** toggles visibility. |

Press **Continue**.

### Platform operator tab

For platform operators. There is no tenant slug: enter **Email** and **Password**, then **Continue**. The page reminds you: *After your password you will enter a code from your authenticator app.*

### Authenticator step

Operators must use an authenticator app (Google Authenticator, Microsoft Authenticator, 1Password, Authy…).

- **First sign-in:** *Set up an authenticator app. The admin console requires one.* Scan the QR code (**QR code for your authenticator app**), type the 6-digit code into **Authenticator code** and press **Confirm and sign in**.
- **Save your recovery codes:** after enrolment you see one-time recovery codes. Each signs you in once if you lose your phone. Use **Copy codes**, store them in a password manager, then press **I have saved these codes**. They are not shown again.
- **Later sign-ins:** *Enter the code from your authenticator app.* Lost your phone? **Can't use your authenticator app? Try another way** lists every option enabled for you (authenticator app, passkey or security key if you registered one, recovery code); pick one, and **Try another way** again to switch back.
- **Start over** cancels this step and returns to the email/password form. **Signing in as** shows which account is being signed in.

### Emergency access

**Use emergency access** switches to the break-glass sign-in for the emergency account configured on the server: the field becomes **Username** (placeholder `admin`). The warning reads: *Use only when no named operator can sign in. This sign-in is audited, platform admins are emailed, and the session lasts at most 1 hour. It may be disabled in production.* **Back to normal sign-in** returns to the regular form.

The **Emergency access** link appears only when the server has the emergency account turned on (always in local development; in production only with `SECUREONE_BREAK_GLASS_ENABLED=true`). If it is off, a sign-in attempt answers *Emergency access is turned off on this server. Sign in on the Platform operator or Organization tab.*

### Messages on the sign-in page

| Message | Meaning |
|---------|---------|
| *You signed out.* | You pressed **Sign out**. |
| *Your session ended (inactivity or maximum session length). Sign in again to continue.* | The console session expired. |
| *Your operator account is ready…* | Setup has just finished. |
| *Enter your organization's tenant slug.* | The Organization tab needs a tenant slug. |
| *Platform operators sign in on the Platform operator tab.* | You tried a platform account on the Organization tab. |
| *That code is not valid.* | Wrong or expired authenticator / recovery code. |

## My profile

Open it by clicking your picture (or initials) and name at the right of the top bar (tooltip **My profile**). The panel says *Account details, password, and MFA*. Operators with a passkey or security key can use it at sign-in (**Use your passkey or security key**); sensitive console actions ask **Verify it's you** again, see [Two-step verification](two-step-verification.md#admin-actions-for-a-user). Close it with **×**.

### Account

**Profile picture:** **Upload picture** (or **Change picture**) takes a PNG, JPEG or WebP file. The console crops it to a square and resizes it to 256 px before sending it, which also drops any camera or location details stored in the photo; the server accepts at most 512 KB and checks that the file really is one of those formats. **Remove** goes back to your initials. The picture shows in the top bar. The emergency account has no picture.

**Email**, **Username** and **Display name**, plus your role badge (**Super admin**, **Emergency account**) and **Member since**. Change what is editable and press **Save changes** (*Profile updated*).

### Password

*Enter your current password, then choose a new one.*

| Field | What to enter |
|-------|---------------|
| **Current password** | Your password today. |
| **New password** | The new password. It must meet the platform password policy. |
| **Confirm new password** | The new password again (*New passwords do not match* if they differ). |

The emergency account cannot change its password here: *The emergency account's password is set in server configuration.*

### Multi-factor authentication

*Your authenticator app is required to sign in to the admin console.* The panel shows **Authenticator app set up** or **No authenticator app**, any passkeys and your recovery codes.

| Button | What it does |
|--------|--------------|
| **Add authenticator app** | Shows a QR code under **Scan QR code with your authenticator app**; type the **Verification code** and press **Confirm**. |
| Remove (authenticator) → **Confirm remove** | Removes your authenticator. *You will set up a new one at next sign-in.* Use it when you change phone. |
| **Generate new recovery codes** | Creates a new list under **Save these recovery codes**. *New recovery codes created. The old ones no longer work.* |
| **Add passkey** | Registers this device's passkey (Windows Hello, Touch ID, a security key). Shows *Passkeys are not supported in this browser* where unavailable. |

## Signing out

Use **Sign out** in the top bar. If an account has no OAuth clients to work with yet, the empty-state panel also offers **Sign out** and **Sign in as platform admin**.
