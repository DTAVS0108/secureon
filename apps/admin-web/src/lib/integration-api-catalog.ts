import { publicAuthServerUrl } from "@/lib/config";

export type ApiField = {
  name: string;
  type: string;
  required?: boolean;
  description?: string;
};

export type ApiResponseDoc = {
  status: number;
  description: string;
  example?: string;
};

export type IntegrationApiEndpoint = {
  id: string;
  group: string;
  method: string;
  path: string;
  description: string;
  authUiMode?: "hosted" | "native" | "both";
  auth?: string;
  contentType?: string;
  queryParams?: ApiField[];
  requestFields?: ApiField[];
  requestExample?: string;
  responses?: ApiResponseDoc[];
  notes?: string[];
  /** Hand-written curl when one generated from the fields above would not fit (several variants). */
  curl?: string;
};

export type ApiCatalogContext = {
  applicationId: string;
  clientId?: string;
  tenantSlug?: string;
};

function json(value: unknown): string {
  return JSON.stringify(value, null, 2);
}

export function buildIntegrationApiCatalog(ctx: ApiCatalogContext): IntegrationApiEndpoint[] {
  const base = publicAuthServerUrl();
  const { applicationId, clientId = "{clientId}", tenantSlug = "{tenantSlug}" } = ctx;

  return [
    {
      id: "oidc-discovery",
      group: "Discovery",
      method: "GET",
      path: `${base}/.well-known/openid-configuration`,
      description: "OIDC provider metadata used to discover authorize, token, JWKS, and userinfo endpoints.",
      authUiMode: "both",
      auth: "None (public)",
      responses: [
        {
          status: 200,
          description: "OIDC configuration document",
          example: json({
            issuer: base,
            authorization_endpoint: `${base}/oauth2/authorize`,
            token_endpoint: `${base}/oauth2/token`,
            jwks_uri: `${base}/oauth2/jwks`,
            userinfo_endpoint: `${base}/userinfo`,
            end_session_endpoint: `${base}/oauth2/logout`,
            scopes_supported: ["openid", "profile", "email"],
            response_types_supported: ["code"],
            grant_types_supported: ["authorization_code", "refresh_token", "client_credentials"],
            code_challenge_methods_supported: ["S256"],
          }),
        },
      ],
    },
    {
      id: "public-manifest",
      group: "Discovery",
      method: "GET",
      path: `${base}/api/v1/applications/${applicationId}`,
      description: "Unauthenticated manifest for client apps — auth methods, feature flags, password policy, verification policy, branding.",
      authUiMode: "both",
      auth: "None (public; requires Public API enabled in settings)",
      responses: [
        {
          status: 200,
          description: "Application manifest",
          example: json({
            application: { id: applicationId, name: "My App", status: "active" },
            authMethods: [{ id: "m_password", enabled: true, implemented: true }],
            featureFlags: [
              { key: "self_service_recovery", enabled: true },
              { key: "self_registration", enabled: true },
            ],
            passwordPolicy: {
              minLength: 8,
              requireUppercase: false,
              requireNumber: false,
              requireSymbol: false,
              requirements: [{ key: "minLength", label: "At least 8 characters" }],
            },
            verification: {
              signup: {
                defaultMode: "link",
                allowedModes: ["link", "otp"],
                otpLength: 6,
                otpTtlSeconds: 600,
              },
              login: {
                identifierTypes: ["email", "phone"],
                passwordlessModes: ["email_otp", "phone_otp", "magic_link"],
                otpLength: 6,
              },
              recovery: {
                passwordResetMode: "link",
                allowedModes: ["link", "otp"],
                allowedChannels: ["email", "phone"],
                otpLength: 6,
              },
            },
            federation: {
              providersEndpoint: `/api/v1/auth/federation/providers?applicationId=${applicationId}`,
              providers: [{ alias: "google", type: "GOOGLE", displayName: "Google" }],
            },
            signup: {
              enabled: true,
              endpoint: `/api/v1/applications/${applicationId}/signup`,
              optionsEndpoint: `/api/v1/applications/${applicationId}/signup`,
            },
            account: {
              forgotPasswordEndpoint: `/api/v1/applications/${applicationId}/account/password/forgot`,
              passwordResetOtpEndpoint: `/api/v1/applications/${applicationId}/account/password/reset/otp`,
              passwordResetEndpoint: `/api/v1/account/password/reset`,
              sessionLoginEndpoint: `/api/v1/applications/${applicationId}/auth/session/login`,
              otpLoginRequestEndpoint: `/api/v1/applications/${applicationId}/auth/otp/request`,
              otpLoginVerifyEndpoint: `/api/v1/applications/${applicationId}/auth/otp/verify`,
              passkeyOptionsEndpoint: `/api/v1/auth/passkey/options`,
              passkeyVerifyEndpoint: `/api/v1/auth/passkey/verify`,
              federationProvidersEndpoint: `/api/v1/auth/federation/providers`,
              verifyOtpConfirmEndpoint: `/api/v1/applications/${applicationId}/account/verify/otp/confirm`,
              signupOptionsEndpoint: `/api/v1/applications/${applicationId}/signup`,
              loginIdentifier: "email or phone (E.164)",
              loginEmailHint: "user@example.com",
              hostedLoginPage: `/login.html?applicationId=${applicationId}`,
              hostedSignupPage: `/account/signup.html?applicationId=${applicationId}`,
              hostedForgotPasswordPage: `/account/forgot-password.html?applicationId=${applicationId}`,
            },
          }),
        },
        { status: 404, description: "Manifest disabled or application not found" },
      ],
      notes: ["Enable under Settings → Public API before calling from your client."],
    },
    {
      id: "signup-options",
      group: "Discovery",
      method: "GET",
      path: `${base}/api/v1/applications/${applicationId}/signup`,
      description: "Signup availability, password rules, verification policy, and account endpoint hints.",
      authUiMode: "both",
      auth: "None (public)",
      responses: [
        {
          status: 200,
          description: "Signup configuration",
          example: json({
            applicationId,
            applicationName: "My App",
            signupEnabled: true,
            signupEndpoint: `/api/v1/applications/${applicationId}/signup`,
            hostedSignupPage: `${base}/account/signup.html?applicationId=${applicationId}`,
            passwordPolicy: {
              minLength: 8,
              requirements: [{ key: "minLength", label: "At least 8 characters" }],
            },
            verification: {
              signup: { defaultMode: "link", allowedModes: ["link", "otp"], otpLength: 6 },
              login: { identifierTypes: ["email", "phone"], passwordlessModes: ["email_otp", "phone_otp"] },
              recovery: { passwordResetMode: "link", allowedModes: ["link", "otp"], allowedChannels: ["email", "phone"] },
            },
            account: {
              forgotPasswordEndpoint: `/api/v1/applications/${applicationId}/account/password/forgot`,
              passwordResetOtpEndpoint: `/api/v1/applications/${applicationId}/account/password/reset/otp`,
              verifyOtpConfirmEndpoint: `/api/v1/applications/${applicationId}/account/verify/otp/confirm`,
            },
            loginEmailHint: "your@email.com",
          }),
        },
      ],
    },
    {
      id: "oauth-authorize",
      group: "Sign-in",
      method: "GET",
      path: `${base}/oauth2/authorize`,
      description: "Hosted login — redirect the browser here to start the authorization code + PKCE flow.",
      authUiMode: "hosted",
      auth: "Browser redirect (session cookie set after login)",
      queryParams: [
        { name: "response_type", type: "string", required: true, description: "Must be code" },
        { name: "client_id", type: "string", required: true, description: `Your OAuth client ID (e.g. ${clientId})` },
        { name: "redirect_uri", type: "string", required: true, description: "Must match a registered redirect URI" },
        { name: "scope", type: "string", required: true, description: "Space-separated scopes, e.g. openid profile email" },
        { name: "state", type: "string", required: true, description: "CSRF token your app validates on callback" },
        { name: "code_challenge", type: "string", required: true, description: "PKCE S256 challenge" },
        { name: "code_challenge_method", type: "string", required: true, description: "S256" },
      ],
      responses: [
        { status: 302, description: "Redirect to login or back to redirect_uri with ?code=&state=" },
        { status: 400, description: "Invalid client_id, redirect_uri, or PKCE parameters" },
      ],
      notes: [
        "After native session login, navigate here in the same browser to complete token exchange.",
      ],
    },
    {
      id: "session-login",
      group: "Sign-in",
      method: "POST",
      path: `${base}/api/v1/applications/${applicationId}/auth/session/login`,
      description:
        "Native login — identifier (email or phone) and password; tenant is resolved from applicationId. Sets an auth-server session cookie (Redis).",
      authUiMode: "native",
      auth: "None (public); sets session cookie on success",
      contentType: "application/json",
      requestFields: [
        { name: "identifier", type: "string", required: true, description: "Email or phone (E.164)" },
        { name: "password", type: "string", required: true, description: "User password (1–128 chars)" },
      ],
      requestExample: json({ identifier: "user@example.com", password: "••••••••" }),
      responses: [
        { status: 204, description: "Login succeeded; session cookie set" },
        {
          status: 401,
          description: "Invalid credentials",
          example: json({
            type: "about:blank",
            title: "Unauthorized",
            status: 401,
            detail: "Invalid email or password.",
          }),
        },
      ],
      notes: [
        "Call with credentials: 'include' from your SPA origin (CORS must allow your app).",
        "Follow with top-level navigation to /oauth2/authorize to obtain tokens.",
        "Legacy POST /api/v1/auth/session/login (tenantSlug + email) is deprecated.",
      ],
    },
    {
      id: "otp-login-request",
      group: "Sign-in",
      method: "POST",
      path: `${base}/api/v1/applications/${applicationId}/auth/otp/request`,
      description:
        "Request a one-time sign-in code by email or SMS (allowed by Verification & OTP › Passwordless modes; SMS also needs an SMS provider).",
      authUiMode: "native",
      auth: "None (public)",
      contentType: "application/json",
      requestFields: [
        { name: "identifier", type: "string", required: true, description: "Email or phone" },
        { name: "channel", type: "string", required: false, description: "email | sms (inferred from identifier if omitted)" },
      ],
      requestExample: json({ identifier: "user@example.com", channel: "email" }),
      responses: [
        {
          status: 200,
          description: "Generic success (anti-enumeration)",
          example: json({ message: "If an account exists, a sign-in code has been sent." }),
        },
      ],
    },
    {
      id: "otp-login-verify",
      group: "Sign-in",
      method: "POST",
      path: `${base}/api/v1/applications/${applicationId}/auth/otp/verify`,
      description: "Verify OTP and create session cookie.",
      authUiMode: "native",
      auth: "None (public); sets session cookie on success",
      contentType: "application/json",
      requestFields: [
        { name: "challengeId", type: "string (uuid)", required: true, description: "From OTP delivery flow" },
        { name: "code", type: "string", required: true, description: "6-digit code" },
      ],
      requestExample: json({ challengeId: "uuid", code: "123456" }),
      responses: [{ status: 204, description: "Login succeeded; session cookie set" }],
    },
    {
      id: "oauth-token",
      group: "Tokens",
      method: "POST",
      path: `${base}/oauth2/token`,
      description: "Exchange authorization code or refresh token for access and refresh tokens.",
      authUiMode: "both",
      auth: "Public clients: none + PKCE. Confidential clients: client_secret_basic or client_secret_post",
      contentType: "application/x-www-form-urlencoded",
      requestFields: [
        { name: "grant_type", type: "string", required: true, description: "authorization_code | refresh_token" },
        { name: "code", type: "string", description: "Authorization code (authorization_code grant)" },
        { name: "redirect_uri", type: "string", description: "Same redirect_uri used in /authorize" },
        { name: "client_id", type: "string", required: true, description: "OAuth client ID" },
        { name: "code_verifier", type: "string", description: "PKCE verifier (public clients)" },
        { name: "refresh_token", type: "string", description: "Refresh token (refresh_token grant)" },
      ],
      requestExample: [
        "# Authorization code exchange",
        "grant_type=authorization_code",
        `&code={authorization_code}`,
        `&redirect_uri=http://localhost:5173/callback`,
        `&client_id=${clientId}`,
        "&code_verifier={pkce_verifier}",
      ].join(""),
      curl: [
        "# 1. Authorization code + PKCE (public client: SPA, mobile, desktop)",
        `curl -i -X POST "${base}/oauth2/token" \\`,
        '  --data-urlencode "grant_type=authorization_code" \\',
        '  --data-urlencode "code={authorization_code}" \\',
        '  --data-urlencode "redirect_uri=http://localhost:5173/callback" \\',
        `  --data-urlencode "client_id=${clientId}" \\`,
        '  --data-urlencode "code_verifier={pkce_verifier}"',
        "",
        "# 2. Authorization code (confidential client: server-side web app)",
        `curl -i -X POST "${base}/oauth2/token" \\`,
        `  -u "${clientId}:{client_secret}" \\`,
        '  --data-urlencode "grant_type=authorization_code" \\',
        '  --data-urlencode "code={authorization_code}" \\',
        '  --data-urlencode "redirect_uri=http://localhost:3000/auth/callback"',
        "",
        "# 3. Refresh the tokens",
        `curl -i -X POST "${base}/oauth2/token" \\`,
        '  --data-urlencode "grant_type=refresh_token" \\',
        '  --data-urlencode "refresh_token={refresh_token}" \\',
        `  --data-urlencode "client_id=${clientId}"`,
        "",
        "# 4. Machine-to-machine (client credentials)",
        `curl -i -X POST "${base}/oauth2/token" \\`,
        `  -u "${clientId}:{client_secret}" \\`,
        '  --data-urlencode "grant_type=client_credentials" \\',
        '  --data-urlencode "scope={scope}"',
      ].join("\n"),
      responses: [
        {
          status: 200,
          description: "Token response",
          example: json({
            access_token: "eyJhbG…",
            token_type: "Bearer",
            expires_in: 3600,
            refresh_token: "rt_…",
            scope: "openid profile email",
            id_token: "eyJhbG…",
          }),
        },
        { status: 400, description: "Invalid grant, code, or client credentials" },
        { status: 401, description: "Invalid client authentication" },
      ],
    },
    {
      id: "oauth-revoke",
      group: "Tokens",
      method: "POST",
      path: `${base}/oauth2/revoke`,
      description: "Revoke a refresh or access token (RFC 7009). Call on logout.",
      authUiMode: "both",
      auth: "Client authentication for confidential clients",
      contentType: "application/x-www-form-urlencoded",
      requestFields: [
        { name: "token", type: "string", required: true, description: "Refresh or access token to revoke" },
        { name: "token_type_hint", type: "string", description: "refresh_token | access_token (optional)" },
        { name: "client_id", type: "string", required: true, description: "OAuth client ID" },
      ],
      requestExample: `token={refresh_token}&token_type_hint=refresh_token&client_id=${clientId}`,
      responses: [
        { status: 200, description: "Token revoked (always returns 200 per RFC 7009)" },
      ],
    },
    {
      id: "oauth-logout",
      group: "Session",
      method: "GET",
      path: `${base}/oauth2/logout`,
      description: "OIDC end-session — clears auth-server session and optionally redirects back to your app.",
      authUiMode: "hosted",
      auth: "Browser redirect",
      queryParams: [
        { name: "id_token_hint", type: "string", description: "ID token from last login (recommended)" },
        { name: "post_logout_redirect_uri", type: "string", description: "Registered post-logout redirect URI" },
        { name: "state", type: "string", description: "Opaque value returned after logout" },
      ],
      responses: [
        { status: 302, description: "Session cleared; redirect to post_logout_redirect_uri if provided" },
      ],
    },
    {
      id: "userinfo",
      group: "Profile",
      method: "GET",
      path: `${base}/userinfo`,
      description:
        "OIDC userinfo claims for the signed-in user, plus their roles and permissions in this application (same as the access token).",
      authUiMode: "both",
      auth: "Authorization: Bearer {access_token}",
      responses: [
        {
          status: 200,
          description: "OIDC claims with application roles and permissions",
          example: json({
            sub: `${tenantSlug}:user@example.com`,
            email: "user@example.com",
            email_verified: true,
            name: "Full name",
            preferred_username: `${tenantSlug}:user@example.com`,
            roles: ["Member", "Order approver"],
            permissions: ["orders:approve", "user:read"],
          }),
        },
        { status: 401, description: "Missing or invalid access token" },
      ],
    },
    {
      id: "account-profile",
      group: "Profile",
      method: "GET",
      path: `${base}/api/v1/account/profile`,
      description: "Extended account profile beyond OIDC userinfo — tenant, status, password flag.",
      authUiMode: "both",
      auth: "Authorization: Bearer {access_token}",
      responses: [
        {
          status: 200,
          description: "Profile object",
          example: json({
            id: "00000000-0000-0000-0000-000000000001",
            sub: "00000000-0000-0000-0000-000000000001",
            email: "user@example.com",
            emailVerified: true,
            displayName: "Full name",
            status: "ACTIVE",
            hasPassword: true,
            tenantSlug,
            tenantName: "Your Tenant",
          }),
        },
        { status: 401, description: "Missing or invalid access token" },
      ],
    },
    {
      id: "password-forgot",
      group: "Password",
      method: "POST",
      path: `${base}/api/v1/applications/${applicationId}/account/password/forgot`,
      description:
        "Request password reset via email link or OTP (email/SMS). Tenant resolved from applicationId. Generic response always (anti-enumeration).",
      authUiMode: "both",
      auth: "None (public; requires self_service_recovery; OTP when Verification & OTP › Password recovery allows it)",
      contentType: "application/json",
      requestFields: [
        { name: "identifier", type: "string", required: true, description: "Email or phone (E.164)" },
        { name: "email", type: "string (email)", description: "Legacy alias for identifier" },
        { name: "mode", type: "string", description: "link | otp — defaults to verification policy" },
        { name: "channel", type: "string", description: "email | phone | sms when mode is otp" },
      ],
      requestExample: json({ identifier: "user@example.com", mode: "otp", channel: "email" }),
      responses: [
        {
          status: 200,
          description: "Generic success (link or OTP sent if account exists)",
          example: json({
            message: "If an account exists, reset instructions have been sent.",
            mode: "otp",
            challengeId: "uuid",
            maskedDestination: "u***@example.com",
            expiresIn: 600,
            channel: "email",
          }),
        },
        { status: 400, description: "Validation error (blank identifier, disallowed mode/channel)" },
        { status: 500, description: "Self-service recovery disabled for this application" },
      ],
      notes: [
        "Link mode returns message only (no challengeId).",
        "Legacy POST /api/v1/account/password/forgot (tenantSlug + email) is deprecated.",
      ],
    },
    {
      id: "password-reset-otp",
      group: "Password",
      method: "POST",
      path: `${base}/api/v1/applications/${applicationId}/account/password/reset/otp`,
      description: "Complete password reset using OTP from forgot-password (recovery flow).",
      authUiMode: "both",
      auth: "None (public)",
      contentType: "application/json",
      requestFields: [
        { name: "challengeId", type: "string (uuid)", required: true, description: "From forgot-password OTP response" },
        { name: "code", type: "string", required: true, description: "4–8 digit OTP code" },
        { name: "password", type: "string", required: true, description: "New password (8–128 chars)" },
      ],
      requestExample: json({ challengeId: "uuid", code: "123456", password: "NewSecurePass1!" }),
      responses: [
        {
          status: 200,
          description: "Password updated",
          example: json({ message: "Password updated. You can sign in with your new password." }),
        },
        { status: 400, description: "Invalid or expired code, or password policy violation" },
      ],
      notes: ["Global variant: POST /api/v1/account/password/reset/otp"],
    },
    {
      id: "password-reset",
      group: "Password",
      method: "POST",
      path: `${base}/api/v1/account/password/reset`,
      description: "Complete password reset using the token from the email link.",
      authUiMode: "both",
      auth: "None (public)",
      contentType: "application/json",
      requestFields: [
        { name: "token", type: "string", required: true, description: "Token from reset email URL" },
        { name: "password", type: "string", required: true, description: "New password (8–128 chars)" },
      ],
      requestExample: json({ token: "{token_from_email}", password: "NewSecurePass1!" }),
      responses: [
        {
          status: 200,
          description: "Password updated",
          example: json({ message: "Password updated. You can sign in with your new password." }),
        },
        { status: 400, description: "Invalid or expired token, or password policy violation" },
      ],
    },
    {
      id: "password-change",
      group: "Password",
      method: "POST",
      path: `${base}/api/v1/account/password/change`,
      description: "Change password while signed in.",
      authUiMode: "both",
      auth: "Authorization: Bearer {access_token}",
      contentType: "application/json",
      requestFields: [
        { name: "currentPassword", type: "string", required: true, description: "Existing password" },
        { name: "newPassword", type: "string", required: true, description: "New password (8–128 chars)" },
      ],
      requestExample: json({ currentPassword: "OldPass1!", newPassword: "NewPass1!" }),
      responses: [
        {
          status: 200,
          description: "Password changed",
          example: json({ message: "Password updated successfully." }),
        },
        { status: 401, description: "Wrong current password or invalid token" },
      ],
      notes: ["Optional header: X-Application-Id for application-scoped password policy."],
    },
    {
      id: "signup",
      group: "Signup",
      method: "POST",
      path: `${base}/api/v1/applications/${applicationId}/signup`,
      description: "Self-service user registration for this application.",
      authUiMode: "both",
      auth: "None (public; signup must be enabled)",
      contentType: "application/json",
      requestFields: [
        { name: "email", type: "string (email)", required: true, description: "User email" },
        { name: "password", type: "string", required: true, description: "Password (8–128 chars)" },
        { name: "phone", type: "string", description: "Optional E.164 phone" },
        { name: "firstName", type: "string", description: "Optional given name" },
        { name: "lastName", type: "string", description: "Optional family name" },
        { name: "displayName", type: "string", description: "Optional display name" },
        { name: "verificationMode", type: "string", description: "link | otp (per verification policy)" },
        { name: "verificationChannel", type: "string", description: "email | phone | sms when mode is otp" },
      ],
      requestExample: json({
        email: "newuser@example.com",
        password: "SecurePass1!",
        phone: "+10000000000",
        verificationMode: "otp",
        verificationChannel: "email",
        firstName: "First name",
        lastName: "Last name",
      }),
      responses: [
        {
          status: 200,
          description: "Account created (link or OTP mode)",
          example: json({
            message: "Account created. Enter the verification code sent to your email.",
            userId: "…",
            challengeId: "uuid",
            maskedDestination: "n***@example.com",
            expiresIn: 600,
            mode: "otp",
            channel: "email",
            emailVerificationRequired: true,
          }),
        },
        { status: 409, description: "Email already registered" },
        { status: 400, description: "Validation or password policy error" },
      ],
    },
    {
      id: "signup-verify-otp",
      group: "Signup",
      method: "POST",
      path: `${base}/api/v1/applications/${applicationId}/account/verify/otp/confirm`,
      description: "Confirm signup verification with OTP code.",
      authUiMode: "both",
      auth: "None (public)",
      contentType: "application/json",
      requestFields: [
        { name: "challengeId", type: "string (uuid)", required: true, description: "From signup OTP response" },
        { name: "code", type: "string", required: true, description: "4–8 digit OTP code" },
        { name: "channel", type: "string", description: "email | phone | sms (phone confirm via global API)" },
      ],
      requestExample: json({ challengeId: "uuid", code: "123456" }),
      responses: [
        {
          status: 200,
          description: "Verification succeeded",
          example: json({ verified: true, emailVerified: true }),
        },
        { status: 400, description: "Invalid or expired code" },
      ],
      notes: ["Global variant: POST /api/v1/account/verify/otp/confirm with channel for phone OTP"],
    },
    {
      id: "scim-groups",
      group: "Provisioning",
      method: "GET",
      path: `${base}/scim/v2/Groups`,
      description: "SCIM 2.0 group list and CRUD (Groups endpoint).",
      auth: "Authorization: Bearer {scim_bearer_token}",
      responses: [
        {
          status: 200,
          description: "Group list",
          example: json({
            schemas: ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
            totalResults: 1,
            Resources: [{ id: "uuid", displayName: "Engineering", members: [] }],
          }),
        },
      ],
      notes: ["POST/PUT/PATCH/DELETE /scim/v2/Groups/{id} for full group lifecycle"],
    },
    {
      id: "m2m-whoami",
      group: "Machine",
      method: "GET",
      path: `${base}/api/v1/m2m/whoami`,
      description: "Verify API key authentication — returns service account identity and M2M scopes.",
      auth: "Authorization: Bearer sk_live_… (API key)",
      responses: [
        {
          status: 200,
          description: "Authenticated service account",
          example: json({
            serviceAccountId: "uuid",
            tenantId: "uuid",
            apiKeyId: "uuid",
            name: "billing-sync",
            scopes: ["m2m:read", "m2m:tenant", "m2m:health"],
          }),
        },
        { status: 401, description: "Invalid or revoked API key" },
        { status: 429, description: "Per-key rate limit exceeded (120/min)" },
      ],
    },
    {
      id: "m2m-tenant",
      group: "Machine",
      method: "GET",
      path: `${base}/api/v1/m2m/tenant`,
      description: "Tenant metadata for the API key's service account.",
      auth: "Authorization: Bearer sk_live_…",
      responses: [
        {
          status: 200,
          description: "Tenant record",
          example: json({ id: "uuid", slug: "tenant-slug", name: "Tenant name", status: "ACTIVE" }),
        },
      ],
    },
    {
      id: "m2m-health",
      group: "Machine",
      method: "GET",
      path: `${base}/api/v1/m2m/health`,
      description: "M2M health probe (requires valid API key).",
      auth: "Authorization: Bearer sk_live_…",
      responses: [{ status: 200, description: "Service healthy", example: json({ status: "UP", service: "secureone-m2m" }) }],
    },
    {
      id: "oauth-token-exchange",
      group: "OAuth",
      method: "POST",
      path: `${base}/oauth2/token`,
      description: "OAuth 2.0 Token Exchange (RFC 8693) — issue a delegated access token from a subject access token.",
      auth: "client_id + client_secret (confidential client)",
      contentType: "application/x-www-form-urlencoded",
      requestFields: [
        {
          name: "grant_type",
          type: "string",
          required: true,
          description: "urn:ietf:params:oauth:grant-type:token-exchange",
        },
        { name: "subject_token", type: "string", required: true },
        {
          name: "subject_token_type",
          type: "string",
          description: "urn:ietf:params:oauth:token-type:access_token",
        },
        { name: "scope", type: "string", description: "Subset of subject token scopes" },
      ],
      responses: [
        {
          status: 200,
          description: "Delegated access token",
          example: json({
            access_token: "eyJ…",
            token_type: "Bearer",
            expires_in: 3600,
            issued_token_type: "urn:ietf:params:oauth:token-type:access_token",
            scope: "openid profile",
          }),
        },
        { status: 400, description: "invalid_grant — bad subject_token or scope" },
      ],
    },
    {
      id: "oauth-par",
      group: "OAuth",
      method: "POST",
      path: `${base}/oauth2/par`,
      description: "Pushed Authorization Request (RFC 9126) — returns request_uri for authorize redirect.",
      auth: "client_id (+ client_secret for confidential clients)",
      contentType: "application/x-www-form-urlencoded",
      requestFields: [
        { name: "client_id", type: "string", required: true },
        { name: "response_type", type: "string", required: true, description: "code" },
        { name: "redirect_uri", type: "string", required: true },
        { name: "scope", type: "string" },
        { name: "code_challenge", type: "string", required: true },
        { name: "code_challenge_method", type: "string", required: true, description: "S256" },
      ],
      notes: [
        "POST stored params as JSON; redirect browser to GET /oauth2/authorize?client_id=…&request_uri=…",
        "Each request_uri is single-use (Redis get-and-delete) and expires in 300s.",
      ],
      responses: [
        {
          status: 201,
          description: "PAR succeeded",
          example: json({ request_uri: "urn:secureone:par:…", expires_in: 300 }),
        },
      ],
    },
    {
      id: "token-introspect",
      group: "OAuth",
      method: "POST",
      path: `${base}/oauth2/introspect`,
      description: "Token introspection (RFC 7662) — check if an access or refresh token is active.",
      auth: "client_id + client_secret (confidential client)",
      contentType: "application/x-www-form-urlencoded",
      requestFields: [
        { name: "token", type: "string", required: true },
        { name: "token_type_hint", type: "string", description: "access_token | refresh_token" },
      ],
      responses: [
        {
          status: 200,
          description: "Introspection result",
          example: json({ active: true, sub: "uuid", scope: "openid profile", exp: 1710000000 }),
        },
      ],
    },
    {
      id: "token-revoke",
      group: "OAuth",
      method: "POST",
      path: `${base}/oauth2/revoke`,
      description: "Token revocation (RFC 7009).",
      auth: "client_id + client_secret (confidential client)",
      contentType: "application/x-www-form-urlencoded",
      requestFields: [
        { name: "token", type: "string", required: true },
        { name: "token_type_hint", type: "string", description: "access_token | refresh_token" },
      ],
      responses: [{ status: 200, description: "Token revoked (or unknown token — still 200 per RFC)" }],
    },
    {
      id: "federation-providers",
      group: "Sign-in",
      method: "GET",
      path: `${base}/api/v1/auth/federation/providers?applicationId=${applicationId}`,
      description: "List enabled federation providers (Google, GitHub, OIDC, SAML 2.0) for sign-in buttons.",
      authUiMode: "both",
      auth: "None (public)",
      responses: [
        {
          status: 200,
          description: "Provider list",
          example: json([
            {
              alias: "google",
              type: "GOOGLE",
              displayName: "Google",
              authMethodId: "m_google",
              startUrl: "/api/v1/auth/federation/google/start",
            },
            { alias: "corp-sso", type: "SAML", displayName: "Corp SSO", authMethodId: "m_saml", startUrl: "/api/v1/auth/federation/corp-sso/start" },
          ]),
        },
      ],
    },
    {
      id: "federation-start",
      group: "Sign-in",
      method: "GET",
      path: `${base}/api/v1/auth/federation/{alias}/start?applicationId=${applicationId}&continue={authorizeUrl}`,
      description:
        "Start sign-in with a provider: OAuth/OIDC redirect, or a signed SAML AuthnRequest (redirect or auto-POST form). Open it in the browser (top-level navigation).",
      authUiMode: "both",
      auth: "None (browser)",
      notes: [
        "continue must be a /oauth2/authorize URL on SecureOne (anything else is ignored).",
        "Failures come back to /login.html?error=federation&reason=expired|failed|not_allowed|unverified_email.",
      ],
      responses: [{ status: 302, description: "To the IdP (or 200 auto-POST form for SAML HTTP-POST binding)" }],
    },
    {
      id: "saml-sp-metadata",
      group: "Sign-in",
      method: "GET",
      path: `${base}/api/v1/auth/federation/saml/{alias}/metadata?applicationId=${applicationId}`,
      description: "SAML SP metadata to import at the company IdP: entity ID, ACS (HTTP-POST), single logout, signing + encryption certificate.",
      authUiMode: "both",
      auth: "None (public)",
      responses: [{ status: 200, description: "application/xml EntityDescriptor" }],
    },
    {
      id: "saml-logout",
      group: "Sign-in",
      method: "GET",
      path: `${base}/api/v1/auth/federation/saml/logout`,
      description:
        "End the SecureOne session; for a SAML session also sign out at the IdP (single logout). OIDC apps get the same through /connect/logout.",
      authUiMode: "both",
      auth: "Session cookie (browser)",
      responses: [{ status: 302, description: "To the IdP's logout endpoint, then /login.html?signedOut=1" }],
    },
    {
      id: "device-authorization",
      group: "Devices",
      method: "POST",
      path: `${base}/oauth2/device_authorization`,
      description: "RFC 8628: a TV app or CLI asks for a code the user enters at verification_uri on another device.",
      authUiMode: "both",
      auth: "client_id (public device client) or client secret",
      contentType: "application/x-www-form-urlencoded",
      requestFields: [
        { name: "client_id", type: "string", required: true, description: "OAuth client with the device_code grant" },
        { name: "scope", type: "string", description: "e.g. profile email (openid is not allowed in this flow)" },
      ],
      requestExample: `client_id=${clientId}&scope=profile%20email`,
      responses: [
        {
          status: 200,
          description: "Codes",
          example: json({
            device_code: "…",
            user_code: "WDJB-MJHT",
            verification_uri: `${base}/account/device.html`,
            verification_uri_complete: `${base}/account/device.html?user_code=WDJB-MJHT`,
            expires_in: 600,
            interval: 5,
          }),
        },
      ],
    },
    {
      id: "device-token",
      group: "Devices",
      method: "POST",
      path: `${base}/oauth2/token`,
      description: "Device polls every `interval` seconds until the user allows or denies on /account/device.html.",
      authUiMode: "both",
      auth: "client_id (public) or client secret",
      contentType: "application/x-www-form-urlencoded",
      requestExample: `grant_type=urn:ietf:params:oauth:grant-type:device_code&device_code={device_code}&client_id=${clientId}`,
      responses: [
        { status: 400, description: "authorization_pending | slow_down | access_denied | expired_token" },
        { status: 200, description: "access_token + refresh_token (no id_token); the device code is single use" },
      ],
    },
    {
      id: "protected-resource-metadata",
      group: "Discovery",
      method: "GET",
      path: `${base}/.well-known/oauth-protected-resource`,
      description: "RFC 9728 metadata for SecureOne's own bearer APIs (authorization server, JWKS, bearer in header).",
      authUiMode: "both",
      auth: "None (public)",
      responses: [
        {
          status: 200,
          description: "Metadata",
          example: json({ resource: base, authorization_servers: [base], jwks_uri: `${base}/oauth2/jwks`, bearer_methods_supported: ["header"] }),
        },
      ],
    },
    {
      id: "passkey-options",
      group: "Sign-in",
      method: "POST",
      path: `${base}/api/v1/auth/passkey/options`,
      description: "Begin WebAuthn passkey authentication — returns publicKey options for navigator.credentials.get().",
      authUiMode: "native",
      auth: "None (public)",
      contentType: "application/json",
      requestFields: [
        { name: "username", type: "string", required: true, description: "tenantSlug:email (lowercase)" },
      ],
      requestExample: json({ username: `${tenantSlug}:user@example.com` }),
      responses: [
        {
          status: 200,
          description: "WebAuthn assertion options",
          example: json({ challengeId: "uuid", publicKey: { challenge: "…", rpId: "localhost", allowCredentials: [] } }),
        },
      ],
    },
    {
      id: "passkey-verify",
      group: "Sign-in",
      method: "POST",
      path: `${base}/api/v1/auth/passkey/verify`,
      description: "Complete passkey sign-in; sets session cookie on success.",
      authUiMode: "native",
      auth: "None (public); sets session cookie on success",
      contentType: "application/json",
      requestFields: [
        { name: "challengeId", type: "string (uuid)", required: true },
        { name: "credential", type: "object", required: true, description: "PublicKeyCredential from browser" },
      ],
      responses: [{ status: 204, description: "Login succeeded; session cookie set" }],
    },
    {
      id: "privacy-export",
      group: "Profile",
      method: "GET",
      path: `${base}/api/v1/account/privacy/export`,
      description: "GDPR data export — profile summary, MFA factor types, linked identity count (no secrets).",
      authUiMode: "both",
      auth: "Authorization: Bearer {access_token}",
      responses: [
        {
          status: 200,
          description: "Export payload",
          example: json({
            exportedAt: "2026-06-13T00:00:00Z",
            userId: "…",
            email: "user@example.com",
            mfaFactors: [{ type: "totp", verified: true }],
            linkedIdentityCount: 1,
          }),
        },
      ],
    },
    {
      id: "privacy-delete-request",
      group: "Profile",
      method: "POST",
      path: `${base}/api/v1/account/privacy/delete-request`,
      description: "Request account deletion: disables the account now; it is removed within 30 days (an administrator can do it sooner, or cancel).",
      authUiMode: "both",
      auth: "Authorization: Bearer {access_token}",
      responses: [
        {
          status: 200,
          description: "Deletion requested",
          example: json({ message: "Account deletion requested. Your account has been disabled and will be removed within 30 days." }),
        },
      ],
    },
  ];
}
