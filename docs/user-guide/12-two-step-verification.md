# Two-step verification

Two-step verification (also called 2FA or MFA) asks for a second proof after the first sign-in step (password, one-time code, magic link, Google/OIDC/SAML). A stolen password alone then can't open the account.

This chapter covers what end users see, the settings for each level (platform, tenant, application), and what admins can do for a user.

## The options

| Option | What it is | Strength |
|--------|-----------|----------|
| **Passkey** | Fingerprint, face or device PIN on the user's phone or computer | Phishing-resistant |
| **Security key** | A USB or NFC key such as a YubiKey | Phishing-resistant |
| **Authenticator app** | A 6-digit code from Google Authenticator, Microsoft Authenticator, 1Password… | Strong |
| **Text message** | A code sent by SMS | Basic |
| **Email code** | A code sent to the user's email | Basic |
| **Backup codes** | Ten single-use codes for when nothing else is at hand | Recovery only |

Text message and email codes are **off by default**: they can be intercepted, and the settings page warns when you turn them on. A code sent to the same place as the first step does not count: someone who signed in with an email code can't use an email code as the second step.

Users can have several options. The one marked **asked first** is offered first; **Try another way** lists the others.

## For end users

### Signing in

After the first step the user sees **Verify it's you** with their default option:

- passkey or security key: the browser asks for the fingerprint, face, PIN or key;
- authenticator app: **Code from your authenticator app**, then **Verify and continue**;
- text or email: **Text a code to …** / **Email a code to …**, then the code (**Send a new code** if it didn't arrive);
- **Use a backup code** when nothing else works. The user is emailed when a backup code is used.

Ticking **Don't ask again on this device** skips the second step on that browser for the number of days the admin set. Wrong codes count toward the account lockout like wrong passwords.

### Setting it up now or later

When an application suggests or requires two-step verification and the user has no option yet, the sign-in shows **Protect your account** (optional) or **Set up two-step verification** (required):

| Mode | What the user sees |
|------|--------------------|
| **Optional** | **Remind me later** (asked again after the set number of days) and, if allowed, **Don't ask again** |
| **Required, in the grace period** | *Set it up by {date}*, with **Remind me later** while skips remain. Reminder emails arrive before the deadline. |
| **Required, deadline passed** | *Set it up now to continue.* No skip. |

After the first option is added the user gets **Save your backup codes** and continues with **I saved them, continue**.

### Account › Security (`/account/security.html`)

| Section | What the user can do |
|---------|----------------------|
| **Two-step verification: {status}** | On, off, required by the administrator, or *Not required for you until {date}* |
| **Ways to verify it's you** | Add a passkey, security key, authenticator app, phone or email code; remove one; choose which is asked first |
| **Backup codes** | See how many are left; **Get new backup codes** (old ones stop working) |
| **Remembered devices** | Forget one device or **Forget all devices** |
| **Turn off two-step verification** | Only when the application doesn't require it |

Removing an option, changing the default, new backup codes and turning it off need a recent second step: the page shows **Verify it's you** first. The user is emailed after every change.

## Settings

Settings are layered: **platform → tenant → application**. Each level starts from the level above and changes only what it needs.

| Where | Who |
|-------|-----|
| **Settings › Authentication › Two-step verification** (platform settings) | Platform operators |
| **Tenants › {tenant} › Two-step verification** | Platform operators and the tenant's Super Admin |
| **Settings › Authentication › Two-step verification** (application settings) | Application admins |

### Locks

At platform and tenant level each setting has a menu on the right that controls the levels below:

| Choice | Effect |
|--------|--------|
| **Tenants & apps may change** / **Apps may change** | Lower levels can change it freely |
| **Only stricter** | Lower levels may only make it stricter (for example Optional → Required, or a shorter grace period) |
| **Locked** | Lower levels can't change it; it shows **Set by platform** (or **Set by platform or tenant**) |

A setting changed at the current level shows **Changed here**. **Reset** goes back to the inherited settings.

### Fields

| Field | Meaning |
|-------|---------|
| **Two-step verification** | **Off**: never asked. **Optional**: users choose and are reminded. **Required**: everyone must set it up. New installations start at **Optional**. |
| **Required strength** | What counts when it is required: any option, strong (authenticator app or better), or phishing-resistant (passkey or security key only) |
| **Options users can use** | The options above. Text message and email show a warning. |
| **Suggested first** | Shown first to users who have it, and recommended at setup |
| **Suggest setup** (optional mode) | Prompt users without an option at sign-in |
| **Ask again after (days)** | How long **Remind me later** lasts in optional mode |
| **Allow don't ask again** | Lets users stop the suggestions for good |
| **Grace period (days, required mode)** | From the first prompt; 0 = set up at the next sign-in |
| **“Later” allowed during the grace period** | How many times the user may skip |
| **Enforce from** | Existing users' grace periods start no earlier than this date. Empty = now. |
| **Reminder emails (days before the deadline)** | For example `7, 1` |
| **“Don't ask again on this device” (days)** | 0 = never offered |
| **“Verify it's you” window (minutes)** | How recent the second step must be before sensitive account changes |
| **Ask users who have an option** (optional mode) | Off: users with an option are only asked when it is required |
| **Trust IdP MFA** | Google, Microsoft, OIDC or SAML sign-ins that report MFA are not asked again. Off by default. |
| **Time to finish the second step (seconds)**, **Wrong codes per sign-in**, **Backup codes per user** | Limits for the sign-in step |

### Applications using OpenID Connect

An application can ask for a fresh second step on one request by sending `acr_values=urn:secureone:acr:mfa` (also accepted: `2`, `urn:secureone:acr:2`) (or `max_age=0` for a full new sign-in) on `/oauth2/authorize`. Sessions also end 24 hours after the sign-in, whatever the activity.

## Admin actions for a user

Open the user (**Users › {user}**) and the **Two-step** tab. The status badge is one of **2FA on**, **2FA off**, **2FA due** (in the grace period), **2FA overdue**, **2FA required** and **2FA exempt**. The users list shows the same badge and can be filtered by it. The tab never shows secrets, only the option, a masked phone or email, and the dates added and last used.

| Action | What it does | Who |
|--------|-------------|-----|
| **Remove** (one option) | Deletes that option. The user is signed out everywhere and emailed. | Tenant admins and above |
| **Reset all** | Deletes every option and backup code, forgets remembered devices, signs the user out and emails them. If the application requires two-step verification they set it up at the next sign-in, with no grace period. | Tenant admins and above |
| **Extend** | Moves the setup deadline. Tenant admins: up to 14 days, once. Super Admins and platform operators: up to 180 days. | Tenant admins and above |
| **Exempt** / **End exemption** | Lets the user sign in without a second step until a date, with a reason. Audited; the user is emailed. | Tenant Super Admins and platform operators |

Before a reset, an exemption, approving a change request, rotating a client secret, issuing a key or token, impersonating, or adding a platform operator, the console asks **Verify it's you**: enter an authenticator code, a recovery code, or use your passkey. It is asked again after a few minutes.

## Console operators

Operators sign in to the console with a password and a second step. Operators with a passkey or security key see **Use your passkey or security key** on the second step; the authenticator code and recovery codes still work.
