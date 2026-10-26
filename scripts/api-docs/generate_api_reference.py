"""Generates docs/api-reference/ from the server's OpenAPI document.

Every endpoint gets its method and path, what it does, how to authenticate, its parameters, request body
fields, success response and a ready-to-run curl command. Pages follow the order an integration or an
operator meets the APIs (see SECTIONS).

    # The server must run with SECUREONE_SWAGGER_ENABLED=true
    curl -s http://localhost:9000/v3/api-docs/all -o openapi.json
    python scripts/api-docs/generate_api_reference.py openapi.json docs/api-reference

Hand-written pages (README.md, 00-getting-started.md) are kept; only the section table in README.md
(between its markers) is rewritten.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

# (file, title, intro, tags). An operation goes to the first section listing one of its tags.
SECTIONS = [
    ("01-oauth-oidc.md", "OAuth 2.1 and OpenID Connect",
     "The authorization server protocol: discovery, authorize, token, revoke, introspect, JWKS, UserInfo, device "
     "flow and OAuth extensions. Most apps only need discovery, authorize, token and UserInfo; an OIDC library "
     "calls them for you.",
     ["OAuth 2.1 / OIDC", "OAuth 2.1 / OIDC — extensions", "consent-page-controller"]),
    ("02-public-and-setup.md", "Public endpoints and first-run setup",
     "Server information, the sign-in method catalog, documentation discovery and the one-time setup of a new "
     "installation. No authentication.",
     ["System", "Documentation", "First-run setup"]),
    ("03-application-sign-in.md", "Application sign-in and sign-up (native UI)",
     "For apps that draw their own sign-in screens instead of redirecting to the hosted page: application "
     "manifest, password and one-time-code sign-in, sign-up, MFA, passkeys, federation and device sign-in.",
     ["Application public API", "Public auth", "Public auth — federation", "Public auth — device sign-in",
      "Public auth — impersonation"]),
    ("04-account.md", "Account self-service",
     "What a signed-in user can do for themselves: password reset and change, email verification, profile, "
     "MFA factors, sessions, email change, connected apps and privacy (export, delete request).",
     ["Account", "Account profile", "Account MFA", "Account sessions", "Account privacy",
      "Account privacy (session)", "Account email change", "Account connected apps"]),
    ("05-machine-and-scim.md", "Machine-to-machine and SCIM provisioning",
     "Calls made by other systems: service accounts with API keys, and SCIM 2.0 user and group provisioning "
     "from an identity provider.",
     ["Machine-to-machine (API keys)", "SCIM 2.0 provisioning"]),
    ("06-admin-sign-in.md", "Admin API: sign-in, context and operator profile",
     "Getting a console session token (sign-in plus MFA), the admin context (who am I, what can I open) and the "
     "signed-in operator's own profile and authenticators.",
     ["Admin — auth", "Admin — context", "Admin — operator profile", "Admin — operator MFA"]),
    ("07-admin-tenants.md", "Admin API: tenants",
     "Tenants and everything inside one: the tenant workspace (roster, application access), tenant roles, "
     "console roles and access, access requests and the tenant's SCIM token.",
     ["Admin — tenants", "Admin — tenant workspace", "Admin — tenant RBAC", "Admin — tenant console roles",
      "Admin — console access", "Admin — console capabilities",
      "Admin — access requests", "Admin — tenant SCIM", "Admin — SCIM"]),
    ("08-admin-users.md", "Admin API: users",
     "User accounts across tenants: create, edit, suspend, delete, passwords, email verification, MFA reset, "
     "unlock and impersonation.",
     ["Admin — users"]),
    ("09-admin-applications-and-clients.md", "Admin API: applications, OAuth clients and change approvals",
     "Application products, their OAuth clients, and changes that wait for a second admin (deleting an OAuth "
     "client returns 202 with a change request; another admin approves it).",
     ["Admin — applications", "Admin — OAuth clients", "Admin — change approvals"]),
    ("10-admin-application-console.md", "Admin API: application console",
     "Everything under /api/admin/v1/applications/{applicationId}/…: the application's users, roles, "
     "permissions, groups, settings and logs.",
     ["Admin — application scope", "Admin — application RBAC", "Admin — application logs"]),
    ("11-admin-security-and-governance.md", "Admin API: security and governance",
     "Platform operators, access review, platform roles, sign-in federation providers, service accounts, "
     "webhooks, the audit log (search and CSV export), sessions and the console overview.",
     ["Admin — overview", "Admin — platform operators", "Admin — access review", "Admin — platform RBAC", "Admin — federation",
      "Admin — service accounts", "Admin — webhooks", "Admin — audit", "Admin — sessions"]),
    ("12-admin-platform-settings.md", "Admin API: platform settings and configuration as code",
     "Platform-wide settings (sign-in methods, password, MFA and token policies, email, SMS, appearance, feature "
     "flags, attack protection, log retention and streaming) and configuration files (export, preview, apply).",
     ["Admin — platform settings", "Attack protection", "Admin — log streams", "Admin — configuration as code"]),
]

METHODS = ("get", "post", "put", "patch", "delete")

# Titles the path rules cannot guess well.
TITLES = {
    ("post", "/api/admin/v1/auth/login"): "Sign in (step 1: password)",
    ("post", "/api/admin/v1/auth/mfa/verify"): "Sign in (step 2: authenticator code)",
    ("post", "/api/admin/v1/auth/mfa/enroll/confirm"): "Sign in (step 2, first time: confirm new authenticator)",
    ("post", "/api/admin/v1/auth/logout"): "Sign out (end the console session)",
    ("get", "/api/admin/v1/me"): "Get the signed-in operator",
    ("get", "/api/admin/v1/context"): "Get admin context (role, applications, console switches)",
    ("post", "/api/v1/account/password/reset/otp"): "Reset password with a one-time code",
    ("post", "/api/v1/account/password/reset"): "Reset password with the emailed link token",
    ("post", "/api/v1/account/password/forgot"): "Start password reset (email link or code)",
    ("post", "/api/admin/v1/tenants/{tenantId}/scim/token"): "Create the tenant's SCIM token",
    ("delete", "/api/admin/v1/tenants/{tenantId}/scim/token"): "Revoke the tenant's SCIM token",
    ("post", "/api/admin/v1/settings/sms/test"): "Send a test SMS",
    ("post", "/api/admin/v1/applications/{applicationId}/settings/sms/test"): "Send a test SMS",
    ("post", "/api/admin/v1/applications/{applicationId}/settings/email/test"): "Send a test email",
    ("delete", "/api/admin/v1/oauth-clients/{id}"): "Request deletion of an OAuth client (needs approval)",
    ("get", "/api/admin/v1/oauth-clients/{id}/delete-request"): "Get the pending delete request of an OAuth client",
    ("get", "/api/admin/v1/change-requests"): "List change requests (every tenant)",
    ("get", "/api/admin/v1/tenants/{tenantId}/change-requests"): "List change requests (one tenant)",
    ("post", "/api/admin/v1/tenants/{tenantId}/admin-operators/{userId}/applications/{applicationId}"): "Make a user tenant admin of an application",
    ("delete", "/api/admin/v1/tenants/{tenantId}/admin-operators/{userId}/applications/{applicationId}"): "Remove a user's tenant admin role for an application",
    ("post", "/api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}"): "Give a user access to an application",
    ("delete", "/api/admin/v1/tenant-workspace/users/{userId}/applications/{applicationId}"): "Remove a user's access to an application",
    ("post", "/api/admin/v1/tenant-workspace/users/{userId}/tenant-admin/{applicationId}"): "Make a user tenant admin of an application",
    ("delete", "/api/admin/v1/tenant-workspace/users/{userId}/tenant-admin/{applicationId}"): "Remove a user's tenant admin role",
    ("post", "/api/admin/v1/applications/{applicationId}/roles/{roleId}/permissions"): "Add a permission to a role",
    ("delete", "/api/admin/v1/applications/{applicationId}/roles/{roleId}/permissions/{permissionId}"): "Remove a permission from a role",
    ("post", "/api/admin/v1/applications/{applicationId}/users/{userId}"): "Add a user to the application",
    ("delete", "/api/admin/v1/applications/{applicationId}/users/{userId}"): "Remove a user from the application",
    ("post", "/api/v1/applications/{applicationId}/account/password/reset/otp"): "Reset password with a one-time code",
    ("get", "/api/v1/applications/resolve"): "Find an application by client ID or host",
    ("get", "/api/v1/account/magic-link/verify"): "Open a magic link (browser): to the second step when needed, else signed in",
    ("post", "/api/v1/account/magic-link/verify"): "Verify a magic link (API): 200 signed in, 428 second step, 403 MFA required",
    ("get", "/api/v1/applications/{applicationId}/signup"): "Get sign-up options",
    ("get", "/api/v1/account/verify/options"): "List verification options",
    ("get", "/api/v1/account/privacy/export"): "Export my data (GDPR)",
    ("get", "/api/v1/m2m/whoami"): "Who am I (service account)",
    ("get", "/api/v1/m2m/tenant"): "Get my tenant (service account)",
    ("get", "/api/v1/m2m/health"): "Check the API key works",
    ("post", "/api/admin/v1/me/mfa/passkey/register/options"): "Start passkey registration",
    ("post", "/api/admin/v1/me/mfa/passkey/register/verify"): "Finish passkey registration",
    ("get", "/api/admin/v1/audit"): "Search the audit log",
    ("get", "/api/admin/v1/audit/export"): "Export the audit log as CSV",
    ("get", "/oauth2/consent"): "Consent screen (hosted page, third-party clients)",
    ("get", "/api/v1/attack-protection"): "Get the CAPTCHA settings for hosted pages (public)",
    ("post", "/api/admin/v1/settings/log-streams/{streamId}/test"): "Send a test entry to a log stream",
    ("post", "/api/v1/account/email/change/confirm"): "Confirm a new email (link token)",
    ("post", "/api/v1/account/email/change/session"): "Change email (browser session)",
    ("post", "/api/v1/account/email/change"): "Change email (access token)",
    ("get", "/api/v1/applications/{applicationId}/branding"): "Get the application's branding (public)",
    ("post", "/api/admin/v1/applications/{applicationId}/users/import"): "Import users from a CSV file",
    ("get", "/api/admin/v1/applications/{applicationId}/settings/email-templates/defaults"): "List default email templates",
    ("post", "/api/admin/v1/applications/{applicationId}/users/{userId}/mfa/methods/{methodId}/reset"): "Reset one MFA method of a user",
    ("post", "/api/admin/v1/access-review/complete"): "Record a completed access review (platform)",
    ("post", "/api/admin/v1/tenants/{tenantId}/access-review/complete"): "Record a completed access review (tenant)",
    ("post", "/api/admin/v1/users/{id}/impersonate"): "Impersonate a user (support)",
    ("post", "/api/admin/v1/applications/{applicationId}/permissions/seed-defaults"): "Add the default permissions",
}
GENERIC_ERRORS = {"400", "401", "403", "404", "409"}
ACTION_WORDS = {
    "approve", "reject", "withdraw", "rotate-secret", "isolate", "reset", "unlock", "verify", "impersonate",
    "complete", "enroll", "confirm", "test", "import", "preview", "logout", "login", "seed-defaults",
    "resend-verification", "set", "remove", "set-password-email", "reset-email", "consume", "start", "callback",
    "request", "export", "apply", "revoke", "introspect", "token", "authorize", "change", "bulk",
}
WORDS = {
    "oauth": "OAuth", "oidc": "OIDC", "mfa": "MFA", "otp": "OTP", "sms": "SMS", "scim": "SCIM", "rbac": "RBAC",
    "api": "API", "jwks": "JWKS", "ldap": "LDAP", "smtp": "SMTP", "saml": "SAML", "m2m": "M2M", "totp": "TOTP",
    "userinfo": "UserInfo", "id": "ID",
}


def words(segment: str) -> str:
    """'oauth-clients' -> 'OAuth clients' (acronyms kept, other words lower case)."""
    parts = [p for p in re.split(r"[-_]", segment) if p]
    out = []
    for p in parts:
        # camelCase segments such as setPassword
        for w in re.sub(r"([a-z])([A-Z])", r"\1 \2", p).split():
            out.append(WORDS.get(w.lower(), w.lower()))
    return " ".join(out)


def cap(text: str) -> str:
    return text[:1].upper() + text[1:]


def singular(text: str) -> str:
    if text.lower() in WORDS or text.lower() in ("sms", "settings", "status", "access", "address"):
        return text
    if text.endswith("ies"):
        return text[:-3] + "y"
    if text.endswith("sses") or text.endswith("ses") and not text.endswith("uses"):
        return text[:-2] if text.endswith("sses") else text[:-1]
    if text.endswith("s") and not text.endswith("ss"):
        return text[:-1]
    return text


def summary_for(method: str, path: str, op: dict) -> str:
    if op.get("summary"):
        return op["summary"].rstrip(".")
    segments = [seg for seg in path.split("/") if seg]
    literal = [seg for seg in segments if not seg.startswith("{") and seg not in ("api", "admin", "v1", "v2")]
    last = segments[-1] if segments else ""
    override = TITLES.get((method, path))
    if override:
        return override
    if last.startswith("{"):
        noun = words(singular(literal[-1])) if literal else "item"
        # POST /users/{userId}/applications/{applicationId} links two existing things.
        verb = {"get": "Get", "put": "Update", "patch": "Update", "delete": "Delete", "post": "Add"}[method]
        if method == "delete" and segments.count("{") == 0 and sum(1 for seg in segments if seg.startswith("{")) > 1:
            verb = "Remove"
        return f"{verb} {noun}"
    noun = words(last)
    parent = words(singular(literal[-2])) if len(literal) > 1 else ""
    if last in ACTION_WORDS or (method == "post" and len(literal) > 1
                                and literal[-2] in ("password", "email", "mfa", "auth", "totp", "passkey", "privacy")):
        if parent and parent not in noun:
            # "approve" + "change request" -> "Approve change request"; "rotate secret" -> "Rotate OAuth client secret"
            first, _, rest = noun.partition(" ")
            return cap(f"{first} {parent} {rest}".strip())
        return cap(noun)
    if method == "get":
        return f"List {noun}" if noun.endswith("s") else f"Get {noun}"
    if method == "post":
        return f"Create {singular(noun)}" if noun.endswith("s") else cap(noun)
    if method in ("put", "patch"):
        return f"Update {noun}"
    return f"Remove {noun}"


def ref(spec: dict, schema: dict | None) -> dict:
    seen = 0
    while schema and "$ref" in schema and seen < 10:
        schema = spec["components"]["schemas"].get(schema["$ref"].split("/")[-1], {})
        seen += 1
    return schema or {}


def ref_name(schema: dict | None) -> str | None:
    if not schema:
        return None
    if "$ref" in schema:
        return schema["$ref"].split("/")[-1]
    if schema.get("type") == "array" and schema.get("items", {}).get("$ref"):
        return schema["items"]["$ref"].split("/")[-1] + "[]"
    return None


def type_of(spec: dict, schema: dict) -> str:
    name = ref_name(schema)
    if name:
        base = name.removesuffix("[]")
        return f"[{base}](13-data-models.md#{base.lower()}){'[]' if name.endswith('[]') else ''}"
    t = schema.get("type", "object")
    if t == "array":
        return f"{type_of(spec, schema.get('items', {}))}[]"
    if schema.get("format") in ("uuid", "date-time", "email", "uri", "int64", "int32", "binary"):
        return f"{t} ({schema['format']})"
    if schema.get("enum"):
        return " | ".join(map(str, schema["enum"]))
    return t


# Exact field names win over the patterns below (whole value, including lists).
FIELD_EXAMPLES = {
    "grantTypes": ["authorization_code", "refresh_token"],
    "scopes": ["openid", "profile", "email"],
    "redirectUris": ["http://localhost:5173/callback"],
    "postLogoutRedirectUris": ["http://localhost:5173/"],
    "type": "spa",
    "subjectType": "public",
    "tokenEndpointAuthMethod": "none",
    "setupFramework": "react",
    "tenantSlug": "acme",
    "emailOrUsername": "admin@example.com",
    "plan": "enterprise",
    "status": "ACTIVE",
    "identifier": "user@example.com",
    "channel": "email",
    "mode": "otp",
    "locale": "en",
    "permissions": ["orders:read"],
    "roles": ["Member"],
    "events": ["user.created"],
    "mfaToken": "{mfaToken}",
    "factorId": "{factorId}",
    "challengeId": "{challengeId}",
    "note": "Short explanation",
    "enabled": True,
    "label": "Production",
    "username": "acme:user@example.com",
    "to": "you@example.com",
    "verificationMode": "otp",
    "verificationChannel": "email",
    "roleType": "custom",
    "effect": "allow",
    "credential": "{credential}",
    "key": "orders:read",
}

EXAMPLE_BY_NAME = [
    (r"email", "user@example.com"), (r"password", "Choose-a-Strong-Pass-1!"), (r"^slug$|tenantslug", "acme"),
    (r"^name$|displayname|applicationname", "Acme Shop"), (r"firstname", "Jane"), (r"lastname", "Doe"),
    (r"clientid", "{clientId}"), (r"redirecturis?$", "http://localhost:5173/callback"), (r"url|uri", "https://app.example.com"),
    (r"phone", "+14155552671"), (r"code$", "123456"), (r"reason|note|description", "Short explanation"),
    (r"scope", "openid profile email"), (r"key$", "key"), (r"token", "{token}"), (r"status", "ACTIVE"),
]


def example(spec: dict, schema: dict, name: str = "", depth: int = 0):
    if name in FIELD_EXAMPLES:
        return FIELD_EXAMPLES[name]
    if "$ref" in schema:
        schema = ref(spec, schema)
    if schema.get("example") is not None:
        return schema["example"]
    if schema.get("enum"):
        return schema["enum"][0]
    t = schema.get("type")
    if t == "object" or "properties" in schema:
        if depth > 2:
            return {}
        return {k: example(spec, v, k, depth + 1) for k, v in (schema.get("properties") or {}).items()}
    if t == "array":
        return [example(spec, schema.get("items", {}), singular(name), depth + 1)]
    if t == "boolean":
        return True
    if t in ("integer", "number"):
        return 10
    if schema.get("format") == "uuid":
        return "{" + (name or "id") + "}"
    if schema.get("format") == "date-time":
        return "2026-10-20T09:00:00Z"
    low = name.lower()
    for pattern, value in EXAMPLE_BY_NAME:
        if re.search(pattern, low):
            return value
    return name or "string"


def auth_for(path: str, op: dict) -> tuple[str, list[str]]:
    schemes = [k for s in op.get("security", []) for k in s]
    if "adminHttpBasic" in schemes or (path.startswith("/api/admin/") and not re.match(r"/api/admin/v1/auth/(login|mfa)", path)):
        return ("Console session token (`Authorization: SecureOne-Admin-Session …`), see "
                "[Getting started](00-getting-started.md#admin-api-get-a-console-session-token)",
                ['-H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"'])
    if "bearerAuth" in schemes or "oauth2Bearer" in schemes:
        return ("Access token (`Authorization: Bearer …`) from the token endpoint",
                ['-H "Authorization: Bearer $ACCESS_TOKEN"'])
    if path.startswith("/api/v1/m2m"):
        return "Service account API key (`Authorization: Bearer sk_…`)", ['-H "Authorization: Bearer $API_KEY"']
    if path.startswith("/scim/"):
        return "Tenant SCIM token (`Authorization: Bearer …`)", ['-H "Authorization: Bearer $SCIM_TOKEN"']
    if path in ("/oauth2/token", "/oauth2/revoke", "/oauth2/introspect", "/oauth2/par", "/oauth2/device_authorization"):
        return ("Client authentication: `-u client_id:client_secret` for confidential clients, `client_id` in "
                "the body for public clients", ['-u "$CLIENT_ID:$CLIENT_SECRET"'])
    if re.match(r"/api/v1/account/(mfa|sessions|privacy/session|passkeys?)", path) or path == "/api/v1/auth/me":
        return "Signed-in session cookie (from a sign-in call with `-c cookies.txt`)", ["-b cookies.txt"]
    if path.startswith("/api/admin/v1/auth/logout"):
        return "Console session token", ['-H "Authorization: SecureOne-Admin-Session $ADMIN_TOKEN"']
    return "None (public)", []


# Protocol endpoints whose parameters depend on the grant or flow: one example per case.
CURLS = {
    ("post", "/oauth2/token"): r'''# 1. Authorization code + PKCE (public client: SPA, mobile, desktop)
curl -s -X POST "$SECUREONE_URL/oauth2/token" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code={authorization_code}" \
  --data-urlencode "redirect_uri=http://localhost:5173/callback" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "code_verifier={pkce_verifier}"

# 2. Authorization code (confidential client: server-side web app)
curl -s -X POST "$SECUREONE_URL/oauth2/token" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code={authorization_code}" \
  --data-urlencode "redirect_uri=http://localhost:3000/auth/callback"

# 3. Refresh tokens
curl -s -X POST "$SECUREONE_URL/oauth2/token" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token={refresh_token}" \
  --data-urlencode "client_id=$CLIENT_ID"

# 4. Machine-to-machine (client credentials)
curl -s -X POST "$SECUREONE_URL/oauth2/token" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "scope={scope}"

# 5. Device flow: poll with the device_code from /oauth2/device_authorization
curl -s -X POST "$SECUREONE_URL/oauth2/token" \
  --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:device_code" \
  --data-urlencode "device_code={device_code}" \
  --data-urlencode "client_id=$CLIENT_ID"''',
    ("get", "/oauth2/authorize"): r'''# Opened in the browser by your app (an OIDC library builds this URL). curl only shows the redirect:
curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" \
  "$SECUREONE_URL/oauth2/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fcallback&scope=openid%20profile%20email&state={state}&code_challenge={pkce_challenge}&code_challenge_method=S256"''',
    ("post", "/oauth2/revoke"): r'''curl -s -X POST "$SECUREONE_URL/oauth2/revoke" \
  --data-urlencode "token={refresh_token}" \
  --data-urlencode "token_type_hint=refresh_token" \
  --data-urlencode "client_id=$CLIENT_ID"
# Confidential clients authenticate instead: -u "$CLIENT_ID:$CLIENT_SECRET"''',
    ("post", "/oauth2/introspect"): r'''curl -s -X POST "$SECUREONE_URL/oauth2/introspect" \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  --data-urlencode "token=$ACCESS_TOKEN"''',
    ("post", "/oauth2/device_authorization"): r'''curl -s -X POST "$SECUREONE_URL/oauth2/device_authorization" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "scope=openid profile"''',
}


def curl_for(spec: dict, method: str, path: str, op: dict, auth_args: list[str]) -> str:
    if (method, path) in CURLS:
        return CURLS[(method, path)]
    url_path = path
    query = [p for p in op.get("parameters", []) if p.get("in") == "query" and p.get("required")]
    if query:
        url_path += "?" + "&".join(f"{p['name']}={{{p['name']}}}" for p in query)
    first = f'curl -s "$SECUREONE_URL{url_path}"' if method == "get" else f'curl -s -X {method.upper()} "$SECUREONE_URL{url_path}"'
    args = [first, *auth_args]
    body = op.get("requestBody", {}).get("content", {})
    if "application/json" in body or "*/*" in body:
        schema = (body.get("application/json") or body.get("*/*")).get("schema", {})
        value = example(spec, schema)
        if value not in ({}, None, "string"):
            text = json.dumps(value, indent=2, ensure_ascii=False).replace("'", "'\\''")
            args += ['-H "Content-Type: application/json"', f"--data '{text}'"]
    elif "application/x-www-form-urlencoded" in body:
        props = ref(spec, body["application/x-www-form-urlencoded"].get("schema", {})).get("properties", {})
        required = set(ref(spec, body["application/x-www-form-urlencoded"].get("schema", {})).get("required", []))
        for name in props:
            if required and name not in required:
                continue
            args.append(f'--data-urlencode "{name}={{{name}}}"')
    elif "multipart/form-data" in body:
        props = ref(spec, body["multipart/form-data"].get("schema", {})).get("properties", {})
        for name, schema in props.items():
            args.append(f'-F "{name}=@users.csv"' if schema.get("format") == "binary" else f'-F "{name}={{{name}}}"')
    return " \\\n  ".join(args)


PARAM_HELP = {
    "id": "ID of the item (UUID)",
    "tenantId": "Tenant ID (UUID)",
    "applicationId": "Application ID (UUID)",
    "userId": "User ID (UUID)",
    "roleId": "Role ID (UUID)",
    "permissionId": "Permission ID (UUID)",
    "groupId": "Group ID (UUID)",
    "factorId": "MFA factor ID (UUID)",
    "providerId": "Identity provider ID (UUID)",
    "assignmentId": "Console access assignment ID (UUID)",
    "keyId": "API key ID (UUID)",
    "reason": "Why the change is needed (shown to approvers)",
    "page": "Page number, from 0",
    "size": "Items per page",
    "search": "Text to search for",
    "status": "Filter by status",
}


def table(rows: list[list[str]], header: list[str]) -> list[str]:
    out = ["| " + " | ".join(header) + " |", "|" + "|".join("---" for _ in header) + "|"]
    for r in rows:
        out.append("| " + " | ".join(c.replace("|", "\\|").replace("\n", " ") for c in r) + " |")
    return out


def anchor(text: str) -> str:
    return re.sub(r"[^a-z0-9 -]", "", text.lower()).strip().replace(" ", "-")


def render_operation(spec: dict, number: str, method: str, path: str, op: dict) -> list[str]:
    title = summary_for(method, path, op)
    out = [f"### {number} {title}", "", f"`{method.upper()} {path}`", ""]
    if op.get("description"):
        out += [op["description"].strip(), ""]
    auth_text, auth_args = auth_for(path, op)
    out += [f"**Authentication:** {auth_text}", ""]

    params = [p for p in op.get("parameters", []) if p.get("in") in ("path", "query") and p.get("name") != "X-Act-As-Email"]
    if params:
        rows = [[f"`{p['name']}`", p["in"], type_of(spec, p.get("schema", {})), "yes" if p.get("required") else "no",
                 p.get("description") or PARAM_HELP.get(p["name"], "")] for p in params]
        out += ["**Parameters**", "", *table(rows, ["Name", "In", "Type", "Required", "Description"]), ""]

    body = op.get("requestBody", {}).get("content", {})
    if body:
        content_type, media = next(iter(body.items()))
        schema = ref(spec, media.get("schema", {}))
        props = schema.get("properties") or {}
        required = set(schema.get("required", []))
        name = ref_name(media.get("schema", {}))
        label = f"**Request body** (`{content_type if content_type != '*/*' else 'application/json'}`" + (f", {type_of(spec, media.get('schema', {}))}" if name else "") + ")"
        if props:
            rows = [[f"`{k}`", type_of(spec, v), "yes" if k in required else "no", v.get("description", "")]
                    for k, v in props.items()]
            out += [label, "", *table(rows, ["Field", "Type", "Required", "Description"]), ""]
        else:
            out += [label + ": " + type_of(spec, media.get("schema", {})), ""]

    responses = op.get("responses", {})
    success = [(code, r) for code, r in responses.items() if code.startswith(("2", "3"))]
    specific_errors = [(code, r) for code, r in responses.items() if not code.startswith(("2", "3")) and code not in GENERIC_ERRORS]
    if success or specific_errors:
        rows = []
        for code, r in success + specific_errors:
            content = r.get("content", {})
            schema = next(iter(content.values())).get("schema", {}) if content else {}
            rows.append([f"`{code}`", r.get("description", ""), type_of(spec, schema) if content else "—"])
        out += ["**Responses**", "", *table(rows, ["Status", "Meaning", "Body"]), ""]

    out += ["```bash", curl_for(spec, method, path, op, auth_args), "```", ""]
    return out


def main(spec_path: str, out_dir: str) -> None:
    spec = json.loads(Path(spec_path).read_text(encoding="utf-8-sig"))
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    tag_to_section = {}
    for index, (_, _, _, tags) in enumerate(SECTIONS):
        for tag in tags:
            tag_to_section.setdefault(tag, index)

    buckets: dict[int, list] = {i: [] for i in range(len(SECTIONS))}
    unplaced = []
    for path, item in spec["paths"].items():
        for method in METHODS:
            op = item.get(method)
            if not op:
                continue
            section = next((tag_to_section[t] for t in op.get("tags", []) if t in tag_to_section), None)
            if section is None:
                unplaced.append(f"{method.upper()} {path} {op.get('tags')}")
                continue
            buckets[section].append((op.get("tags", [""])[0], path, METHODS.index(method), method, op))
    if unplaced:
        raise SystemExit("Operations with no section (add their tag to SECTIONS):\n" + "\n".join(unplaced))

    index_rows = []
    for i, (file, title, intro, tags) in enumerate(SECTIONS, start=1):
        ops = buckets[i - 1]
        ops.sort(key=lambda o: (tags.index(o[0]) if o[0] in tags else 99, o[1], o[2]))
        lines = [
            f"# {i}. {title}",
            "",
            "[API reference](README.md) › " + title,
            "",
            "> Generated from the OpenAPI document by `scripts/api-docs/generate_api_reference.py`. Do not edit by "
            "hand: change the controllers and regenerate.",
            "",
            intro,
            "",
            "Set the variables from [Getting started](00-getting-started.md) first (`SECUREONE_URL`, and the token "
            "the endpoint needs). Replace `{placeholders}` with real values.",
            "",
            "## Contents",
            "",
        ]
        current = None
        body: list[str] = []
        number = 0
        group_no = 0
        for tag, path, _, method, op in ops:
            if tag != current:
                current = tag
                group_no += 1
                number = 0
                heading = tag.replace("Admin — ", "").replace("Public auth — ", "")
                heading = heading[:1].upper() + heading[1:]
                lines.append(f"- [{i}.{group_no} {heading}](#{anchor(f'{i}.{group_no} {heading}')})")
                body += [f"## {i}.{group_no} {heading}", ""]
                tag_info = next((t for t in spec.get("tags", []) if t.get("name") == tag), None)
                if tag_info and tag_info.get("description"):
                    body += [tag_info["description"], ""]
            number += 1
            title_text = summary_for(method, path, op)
            lines.append(f"  - [{method.upper()} `{path}`](#{anchor(f'{i}.{group_no}.{number} {title_text}')}) — {title_text}")
            body += render_operation(spec, f"{i}.{group_no}.{number}", method, path, op)
        (out / file).write_text("\n".join(lines + [""] + body).rstrip() + "\n", encoding="utf-8")
        index_rows.append([f"{i}", f"[{title}]({file})", str(len(ops)), intro.split(". ")[0].rstrip(".") + "."])

    models = ["# 13. Data models", "", "[API reference](README.md) › Data models", "",
              "> Generated from the OpenAPI document. Request and response types used by the endpoints, A to Z.", ""]
    for name in sorted(spec.get("components", {}).get("schemas", {}), key=str.lower):
        schema = spec["components"]["schemas"][name]
        models += [f"## {name}", ""]
        if schema.get("description"):
            models += [schema["description"], ""]
        props = schema.get("properties") or {}
        required = set(schema.get("required", []))
        if props:
            rows = [[f"`{k}`", type_of(spec, v), "yes" if k in required else "no", v.get("description", "")]
                    for k, v in props.items()]
            models += table(rows, ["Field", "Type", "Required", "Description"]) + [""]
        else:
            models += [f"Type: {type_of(spec, schema)}", ""]
    (out / "13-data-models.md").write_text("\n".join(models).rstrip() + "\n", encoding="utf-8")
    index_rows.append(["13", "[Data models](13-data-models.md)", str(len(spec.get("components", {}).get("schemas", {}))),
                       "Every request and response type with its fields."])

    # The section table in README.md sits between these markers and is rewritten on every run.
    readme = out / "README.md"
    if readme.exists():
        text = readme.read_text(encoding="utf-8")
        start, end = "<!-- sections:start -->", "<!-- sections:end -->"
        if start in text and end in text:
            rows = "\n".join(table(index_rows, ["#", "Section", "Endpoints", "What it covers"]))
            text = text[: text.index(start) + len(start)] + "\n" + rows + "\n" + text[text.index(end):]
            readme.write_text(text, encoding="utf-8")
    total = sum(len(b) for b in buckets.values())
    print(f"{total} endpoints in {len(SECTIONS)} sections -> {out}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
