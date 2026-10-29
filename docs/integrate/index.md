---
title: Integrate applications
summary: Connect your web, mobile and back-end applications to SecureOne
order: 3
audience: public
---

# Integrate applications

Any application that speaks OAuth 2.1 or OpenID Connect can use SecureOne, usually through a standard library for
your language. SAML 2.0 and SCIM 2.0 are available for company single sign-on and user provisioning.

## Steps

1. **Register the application** in the console: **Connect an app** walks you through it and gives you the client
   ID and settings. See [Applications and OAuth clients](applications-and-oauth-clients.md).
2. **Choose how users sign in**: SecureOne's hosted pages, or your own screens. See
   [Hosted and native sign-in](hosted-and-native-sign-in.md).
3. **Add sign-in to your code** with the [Integration guide](integration-guide.md).
4. **Check access** in your application with the roles and permissions in the token. See
   [Roles and access](roles-and-access.md).

| Topic | Read |
|---|---|
| Email and phone verification, one-time codes, SMS | [Verification and one-time codes](verification-and-one-time-codes.md) |
| Every endpoint with examples | [API reference](../api/index.md) |
| Roles, permissions and groups in the console | [Application roles and permissions](application-rbac.md) (signed-in operators) |
