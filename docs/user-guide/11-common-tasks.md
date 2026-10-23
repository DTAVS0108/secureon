# 11. Common tasks

[User guide](README.md) › Common tasks

Step-by-step recipes. Each step names the page and the exact button.

## Set up a new organization with its first app

1. **Tenant › Tenants › + New tenant**: enter **Name** and **Slug**, pick **Plan**, **Create tenant**.
2. **Connect an app**: pick the app kind and **Framework**, choose the new **Tenant**, **Application › New application…**, fill **Application name**, **Client ID** and **Redirect URL**, tick **Allow a test sign-in from the console**, **Create client**.
3. Copy the client secret (web apps and backend services) into your server's environment.
4. **Test sign-in ↗** to confirm the setup.
5. **Users & roles** → **+ Invite user** to add the first users.

## Register an app with full control of OAuth settings

1. **OAuth clients › + Register application & OAuth client**.
2. Fill the **Application product** section (**Display name**, **Tenant**, **Slug**).
3. Pick a **Client template**, then adjust **Grant types**, **Scopes**, **Redirect URIs** and **Require PKCE**.
4. **Register application & client**, copy the secret, **Done**.

Details: [OAuth clients](04-oauth-clients.md#register-application--oauth-client).

## Add a mobile app next to an existing web app

1. **OAuth clients › + Add OAuth client**.
2. **Application product**: the existing application. **Client template**: **Native (public + PKCE)**.
3. **Client ID**: for example `shop-mobile`. **Redirect URIs**: `com.example.shop:/oauthredirect`.
4. **Register client**. Both apps now share the same users and roles.

## Go live with production URLs

1. Open the client page (**OAuth clients** → the client ID).
2. **Edit client**: add the production **Redirect URIs** and **Post-logout redirect URIs**, one per line. Remove `localhost` entries you no longer need.
3. **Save changes**.

## A client secret leaked

1. Client page → **Rotate client secret**. Copy the new secret.
2. Update the server's environment immediately; the old secret has already stopped working.
3. Check **Audit log** for unexpected token activity.

## Give someone admin access to one or more applications

1. **Tenant › Tenants** → the tenant → **Tenant roster** (or **Admin console access**).
2. **Grant console access →** (or **Manage access →**) on the person's row.
3. **Role**: **Application Admin** (one app), **Tenant Admin** (chosen apps) or **Tenant Super Admin** (all apps). Choose the applications.
4. **Grant console access**.
5. In the application's **Users** page, open the person and use **Set-password** (email) or **Set password now**, so they can sign in. They sign in on the **Organization** tab with the tenant slug, then set up an authenticator.

## Add another platform operator

**Access review › Platform operators**: **Email**, **First name**, **Last name**, **Add platform operator**. They receive an email to set a password, then set up MFA at first sign-in.

## A user is locked out

A lock ends by itself after the lockout duration (longer for repeated lockouts), or when the user resets their password.

1. Application console › **Users** → the user.
2. **Security**: press **Unlock account**. If they forgot their password, **Reset** sends a reset email, or **Set password now** sets one directly.
3. Lost phone or key: **Two-step** → **Reset all** (or **Remove** one option); they set it up again at their next sign-in. Need time first? **Extend** or **Exempt** them.

## An operator lost their authenticator

Another platform operator can't see operator factors in the application console, so use one of:

- The operator signs in with a **Recovery code** (**Try another way** → **Recovery code**), then in **My profile** removes the old authenticator and adds a new one.
- If every operator is locked out, use **Use emergency access** on the sign-in page with the emergency account, then fix the operator account.

## Turn on two-step verification for an application

1. Application **Settings › Feature flags**: switch on **Two Factor Authentication**, then **Settings › Authentication › Two Factor Authentication**.
2. Choose **Optional** (users are invited and reminded) or **Required**.
3. For **Required**, set the **Grace period** and how many times users may choose **“Later”**, and optionally **Enforce from** so existing users get time.
4. Check **Options users can use**. Text message and email codes are off by default.

Settings locked by the platform or tenant show **Set by platform**. See [Two-step verification](12-two-step-verification.md).

## Allow public sign-up

1. Application **Settings › Feature flags**: switch on **Self Registration**.
2. **Settings › Authentication › Verification & OTP › Signup verification**: choose **Email link** or **OTP code**.
3. Make sure SMTP works: **Notifications › Send test email**.

## Add "Sign in with Google"

1. Create OAuth credentials in Google Cloud Console, with the redirect URL SecureOne shows for the provider.
2. Application **Settings › Authentication › Sign-in methods**: switch on **Identity federation**.
3. **Add provider**: **Provider type** **Google OIDC**, **Alias** `google`, **Display name** *Continue with Google*, **OAuth client ID** and **OAuth client secret**.
4. Save and check the button on the sign-in page.

## Connect enterprise SSO (SAML) and SCIM

1. **Add provider** with **Provider type** **SAML 2.0 (enterprise SSO)** and the customer's **IdP metadata URL**.
2. Platform **Settings › For applications › SCIM 2.0 provisioning**: **Enable SCIM** and set a **Bearer token**.
3. Give the customer's IT team the SCIM base URL (`/scim/v2`) and the token.

## Copy a tenant's setup to production

**Configuration as code**: export the tenant, keep the YAML in Git, then **Upload file › Preview changes › Apply** on production. See [chapter 9](09-configuration-as-code.md).

## Investigate a failed sign-in

1. Application console › **Sessions**: find the attempt (filter **Failed**).
2. Click its session ID to open **Application logs** for that session.
3. Look for **ERROR** / **WARN** lines; the **Request ID** links related events.

---

## Coming soon

These appear in the console but do not work yet:

| Item | Where |
|------|-------|
| **LDAP / AD** import and directory sync | Settings › User directory; Users › Import |
| **Push notifications** channel | Settings › Notifications |
| Top-bar **Search…** | Console top bar |
| Tenant SCIM token screen (use the API or a configuration file) | Tenant page |
