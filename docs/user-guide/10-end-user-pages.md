# 10. End-user pages

[User guide](README.md) › End-user pages

These are the pages SecureOne hosts for the people who sign in to *your* applications. Their look follows the application's **Appearance** settings, and which options appear follows its **Authentication** settings and feature flags. If your app has its own screens, point SecureOne at them in **Settings › Integration › Authentication UI** ([chapter 7](07-settings.md#integration-application)).

Most pages take `?applicationId=…` so they show the right application's options and branding.

## Sign in (`/login.html`)

Your app sends users here through the standard OAuth `/oauth2/authorize` redirect.

| Element | Meaning |
|---------|---------|
| **Tenant slug** | Shown only when the application cannot be determined; most users never see it. |
| **Email** / **Email or mobile number** | The sign-in identifier (per **Identifier types** in Verification settings). |
| **Password** + **Sign in** | Password sign-in. |
| **Sign in with a one-time code** → **Send code** → **Code** → **Verify and sign in** | Passwordless OTP by email or SMS. |
| **Sign in with magic link** | Opens the magic-link page. |
| **Sign in with passkey** | Device passkey (Windows Hello, Touch ID, security key). |
| Provider buttons | For example *Continue with Google*, from Identity federation. |
| **Sign in with password** | Switches back from another method. |
| **Forgot password** | Opens the forgot-password page (when **Self-service Recovery** is on). |
| **Create an account** | Opens sign-up (when **Self Registration** is on). |
| **Authenticator code** + **Verify and continue** | The MFA step when MFA is required or the user has enrolled. |
| **Recovery code** + **Verify recovery code** | Instead of the authenticator code. |

## Create account (`/account/signup.html`)

Visible only when the **Self Registration** feature flag is on.

| Field | What to enter |
|-------|---------------|
| **First name**, **Last name** | The user's name |
| **Email** | Sign-in email |
| **Mobile number** | When phone is an allowed identifier or verification channel, for example `+91 98765 43210` |
| **Password** | Must meet the application's password policy; the checklist shows each rule |

After **Create account**, the user confirms by email link or enters a **Verification code** and presses **Verify** (per **Signup verification**). **Already have an account? Sign in** goes back.

## Forgot password (`/account/forgot-password.html`)

| Field | What to enter |
|-------|---------------|
| **Email or phone (E.164)** | For example `you@example.com` or `+918851512349` |
| **Reset method** | Email link or one-time code (per **Password recovery** settings) |
| **Send code via** | Email or SMS, for the code method |

**Send reset instructions** sends the link or code. With a code, the page then asks for the **Verification code** and a **New password**, and **Update password** finishes. For security the page always reports success, even for unknown emails.

## Reset your password (`/account/reset-password.html`)

Opened from the reset email link. Enter **New password** and **Confirm password**, then **Update password**.

## Set your password (`/account/set-password.html`)

Opened from an invitation or set-password email. Enter **New password** and **Confirm password**, then **Save password**.

## Magic link sign-in (`/account/magic-link.html`)

Enter **Email** and press **Send sign-in link**. The email link signs the user in once; it expires after a short time.

## Connect a device (`/account/device.html`)

For TVs and command-line apps using the device-code grant. The device shows a short code; the user opens this page on their phone or computer, signs in if needed, enters the **Code** (format `XXXX-XXXX`) and presses **Continue** to approve. **Not my device** refuses the request.

## Account security (`/account/security.html`)

Self-service security for signed-in users.

| Section | What the user can do |
|---------|----------------------|
| **MFA factors** | **Add authenticator app** (scan the QR code, enter the 6-digit code, **Confirm**), **Add passkey**, and remove factors |
| **Sessions** | See where they are signed in; **Sign out other devices** |
| **Privacy** | Link to the privacy page |

## Privacy & personal data (`/account/privacy.html`)

| Control | What it does |
|---------|--------------|
| **Session (recommended)** / **Access token** | How the page proves who the user is: the current sign-in session, or an **OAuth access token** pasted in (`eyJhbG…`) |
| **Download my data** / **Export with token** | Downloads the user's personal data (GDPR export); **Export preview** shows it first |
| **Request account deletion** / **Delete request with token** | Asks for the account to be deleted |

## Test sign-in (`/account/connect-test.html`)

Used by the console's **Test sign-in ↗** button after [Connect an app](03-connect-an-app.md). **Start test sign-in** runs a real sign-in against the new client and shows the tokens you get back, so you can check the setup before writing code.
