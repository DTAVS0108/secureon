# 13. Data models

[API reference](README.md) › Data models

> Generated from the OpenAPI document. Request and response types used by the endpoints, A to Z.

## AddOperatorRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string | no |  |
| `firstName` | string | no |  |
| `lastName` | string | no |  |

## AdminContextResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `actAsEmail` | string | no |  |
| `applications` | [ApplicationContextItem](13-data-models.md#applicationcontextitem)[] | no |  |
| `breakGlass` | boolean | no |  |
| `clientSetupCodeEnabled` | boolean | no |  |
| `configAsCodeEnabled` | boolean | no |  |
| `connectAppEnabled` | boolean | no |  |
| `consoleFlags` | object | no |  |
| `displayName` | string | no |  |
| `email` | string | no |  |
| `oauthClientCount` | integer (int64) | no |  |
| `operatorTier` | string | no |  |
| `platformSuperAdmin` | boolean | no |  |
| `principal` | string | no |  |
| `sessionExpiresAt` | string (date-time) | no |  |
| `sessionIdleTimeoutSeconds` | integer (int64) | no |  |
| `tenantId` | string (uuid) | no |  |
| `tenantName` | string | no |  |
| `tenantSlug` | string | no |  |
| `userId` | string (uuid) | no |  |

## AdminLoginRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `emailOrUsername` | string | no |  |
| `password` | string | no |  |
| `tenantSlug` | string | no |  |

## AdminSetPasswordRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `password` | string | yes |  |

## ApplicationContextItem

| Field | Type | Required | Description |
|---|---|---|---|
| `canManageClients` | boolean | no |  |
| `consoleFeatures` | string[] | no |  |
| `id` | string (uuid) | no |  |
| `name` | string | no |  |
| `permissions` | string[] | no |  |
| `slug` | string | no |  |
| `status` | string | no |  |
| `tenantId` | string (uuid) | no |  |
| `tenantName` | string | no |  |
| `tenantSlug` | string | no |  |

## ApplicationCreateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `name` | string | yes |  |
| `slug` | string | no |  |
| `status` | string | no |  |
| `tenantId` | string (uuid) | yes |  |

## ApplicationLogResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `id` | string (uuid) | no |  |
| `ip` | string | no |  |
| `level` | string | no |  |
| `logger` | string | no |  |
| `message` | string | no |  |
| `metadata` | object | no |  |
| `principal` | string | no |  |
| `requestId` | string | no |  |
| `sessionId` | string | no |  |
| `tenantId` | string (uuid) | no |  |
| `timestamp` | string (date-time) | no |  |
| `userAgent` | string | no |  |

## ApplicationResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `createdAt` | string (date-time) | no |  |
| `description` | string | no |  |
| `id` | string (uuid) | no |  |
| `name` | string | no |  |
| `oauthClientCount` | integer (int64) | no |  |
| `schemaName` | string | no |  |
| `slug` | string | no |  |
| `status` | string | no |  |
| `tenantId` | string (uuid) | no |  |
| `updatedAt` | string (date-time) | no |  |

## ApplicationUpdateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `name` | string | yes |  |
| `status` | string | no |  |

## AuditEventResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `action` | string | no |  |
| `actor` | string | no |  |
| `id` | string (uuid) | no |  |
| `ip` | string | no |  |
| `result` | string | no |  |
| `target` | string | no |  |
| `timestamp` | string (date-time) | no |  |

## BulkRosterImportRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `userIds` | string (uuid)[] | no |  |

## ChallengeRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `mfaChallengeId` | string (uuid) | yes |  |

## Change

| Field | Type | Required | Description |
|---|---|---|---|
| `action` | string | no |  |
| `details` | string[] | no |  |
| `kind` | string | no |  |
| `target` | string | no |  |

## ChangePasswordRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `currentPassword` | string | yes |  |
| `newPassword` | string | yes |  |

## ChangeRequestView

| Field | Type | Required | Description |
|---|---|---|---|
| `action` | string | no |  |
| `applicationId` | string (uuid) | no |  |
| `canDecide` | boolean | no |  |
| `decidedAt` | string (date-time) | no |  |
| `decidedBy` | string | no |  |
| `decisionNote` | string | no |  |
| `id` | string (uuid) | no |  |
| `mine` | boolean | no |  |
| `reason` | string | no |  |
| `requestedAt` | string (date-time) | no |  |
| `requestedBy` | string | no |  |
| `status` | string | no |  |
| `targetId` | string (uuid) | no |  |
| `targetLabel` | string | no |  |
| `tenantId` | string (uuid) | no |  |
| `tenantName` | string | no |  |

## CodeConfirmRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no |  |

## CodeSendRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `method` | string | no |  |
| `phone` | string | no |  |

## CodeVerifyRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |
| `rememberDevice` | boolean | no |  |

## CompleteRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no |  |
| `email` | string | no |  |
| `firstName` | string | no |  |
| `lastName` | string | no |  |
| `password` | string | no |  |

## CompleteReviewRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `entriesReviewed` | integer (int32) | no |  |
| `note` | string | no |  |

## ConfirmRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `code` | string | yes |  |
| `factorId` | string (uuid) | yes |  |

## ConsoleAccessAssignment

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `applicationName` | string | no |  |
| `displayName` | string | no |  |
| `email` | string | no |  |
| `id` | string (uuid) | no |  |
| `roleType` | APPLICATION_ADMIN \| TENANT_ADMIN \| TENANT_SUPER_ADMIN | no |  |
| `tenantId` | string (uuid) | no |  |
| `userId` | string (uuid) | no |  |

## CreatedSecret

| Field | Type | Required | Description |
|---|---|---|---|
| `kind` | string | no |  |
| `name` | string | no |  |
| `secret` | string | no |  |

## DecisionRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `note` | string | no |  |

## EmailRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string (email) | yes |  |

## EnrollRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `label` | string | no |  |

## Entry

| Field | Type | Required | Description |
|---|---|---|---|
| `accountStatus` | string | no |  |
| `applicationId` | string (uuid) | no |  |
| `applicationName` | string | no |  |
| `assignmentId` | string (uuid) | no |  |
| `displayName` | string | no |  |
| `email` | string | no |  |
| `expiresAt` | string (date-time) | no |  |
| `flags` | string[] | no |  |
| `grantedAt` | string (date-time) | no |  |
| `grantedBy` | string | no |  |
| `lastLoginAt` | string (date-time) | no |  |
| `mfaEnrolled` | boolean | no |  |
| `role` | string | no |  |
| `tenantId` | string (uuid) | no |  |
| `tenantName` | string | no |  |
| `tenantSlug` | string | no |  |
| `userId` | string (uuid) | no |  |

## ExemptRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `reason` | string | no |  |
| `until` | string | no |  |

## ExtendRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `days` | integer (int32) | no |  |

## FeatureOverride

| Field | Type | Required | Description |
|---|---|---|---|
| `effect` | string | no |  |
| `featureKey` | string | no |  |

## ForgotPasswordRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `channel` | string | no |  |
| `email` | string | no |  |
| `identifier` | string | no |  |
| `mode` | string | no |  |

## GrantConsoleAccessRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `roleType` | string | no |  |
| `userId` | string (uuid) | no |  |

## GroupCreateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | yes |  |
| `description` | string | no |  |
| `memberUserIds` | string (uuid)[] | no |  |
| `name` | string | yes |  |
| `roleIds` | string (uuid)[] | no |  |
| `tenantId` | string (uuid) | yes |  |

## GroupDetailResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `createdAt` | string (date-time) | no |  |
| `description` | string | no |  |
| `id` | string (uuid) | no |  |
| `memberCount` | integer (int32) | no |  |
| `memberUserIds` | string (uuid)[] | no |  |
| `members` | [GroupMemberResponse](13-data-models.md#groupmemberresponse)[] | no |  |
| `name` | string | no |  |
| `roleCount` | integer (int32) | no |  |
| `roleIds` | string (uuid)[] | no |  |
| `roles` | [RoleRefResponse](13-data-models.md#rolerefresponse)[] | no |  |
| `tenantId` | string (uuid) | no |  |

## GroupMemberResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `addedAt` | string (date-time) | no |  |
| `displayName` | string | no |  |
| `email` | string | no |  |
| `emailVerified` | boolean | no |  |
| `id` | string (uuid) | no |  |
| `status` | string | no |  |
| `username` | string | no |  |

## GroupSummaryResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `createdAt` | string (date-time) | no |  |
| `description` | string | no |  |
| `id` | string (uuid) | no |  |
| `memberCount` | integer (int32) | no |  |
| `name` | string | no |  |
| `roleCount` | integer (int32) | no |  |
| `tenantId` | string (uuid) | no |  |

## GroupUpdateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `memberUserIds` | string (uuid)[] | no |  |
| `name` | string | yes |  |
| `roleIds` | string (uuid)[] | no |  |

## ImpersonationTokenResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `consumeUrl` | string | no |  |
| `expiresInSeconds` | integer (int32) | no |  |
| `token` | string | no |  |

## LoginEventResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `device` | string | no |  |
| `id` | string (uuid) | no |  |
| `ip` | string | no |  |
| `location` | string | no |  |
| `method` | string | no |  |
| `result` | string | no |  |
| `sessionId` | string | no |  |
| `timestamp` | string (date-time) | no |  |
| `userEmail` | string | no |  |
| `userId` | string (uuid) | no |  |

## MfaEnrollConfirmRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no |  |
| `factorId` | string | no |  |
| `mfaToken` | string | no |  |

## MfaFactorResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | no |  |
| `label` | string | no |  |
| `type` | string | no |  |
| `verified` | boolean | no |  |

## MfaVerifyRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no |  |
| `mfaToken` | string | no |  |
| `recoveryCode` | string | no |  |

## OAuthClientCreateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `applicationName` | string | no |  |
| `clientId` | string | no |  |
| `grantTypes` | string[] | no |  |
| `pkceRequired` | boolean | no |  |
| `postLogoutRedirectUris` | string[] | no |  |
| `redirectUris` | string[] | no |  |
| `scopes` | string[] | no |  |
| `setupFramework` | string | no |  |
| `status` | string | no |  |
| `subjectType` | string | no |  |
| `tenantId` | string (uuid) | no |  |
| `tokenEndpointAuthMethod` | string | no |  |
| `type` | string | no |  |

## OAuthClientCreateResult

| Field | Type | Required | Description |
|---|---|---|---|
| `client` | [OAuthClientResponse](13-data-models.md#oauthclientresponse) | no |  |
| `clientSecret` | string | no |  |

## OAuthClientResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `applicationName` | string | no |  |
| `clientId` | string | no |  |
| `clientSecretConfigured` | boolean | no |  |
| `confidential` | boolean | no |  |
| `createdAt` | string (date-time) | no |  |
| `grantTypes` | string[] | no |  |
| `id` | string (uuid) | no |  |
| `oAuthEndpoints` | [OAuthEndpoints](13-data-models.md#oauthendpoints) | no |  |
| `pkceRequired` | boolean | no |  |
| `postLogoutRedirectUris` | string[] | no |  |
| `redirectUris` | string[] | no |  |
| `scopes` | string[] | no |  |
| `setupFramework` | string | no |  |
| `status` | string | no |  |
| `subjectType` | string | no |  |
| `tenantId` | string (uuid) | no |  |
| `tokenEndpointAuthMethod` | string | no |  |
| `type` | string | no |  |
| `updatedAt` | string (date-time) | no |  |

## OAuthClientSecretResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `clientSecret` | string | no |  |

## OAuthClientUpdateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `grantTypes` | string[] | no |  |
| `pkceRequired` | boolean | no |  |
| `postLogoutRedirectUris` | string[] | no |  |
| `redirectUris` | string[] | no |  |
| `scopes` | string[] | no |  |
| `setupFramework` | string | no |  |
| `status` | string | no |  |
| `subjectType` | string | no |  |
| `tokenEndpointAuthMethod` | string | no |  |
| `type` | string | no |  |

## OAuthEndpoints

| Field | Type | Required | Description |
|---|---|---|---|
| `authorizationEndpoint` | string | no |  |
| `issuer` | string | no |  |
| `jwksUri` | string | no |  |
| `tokenEndpoint` | string | no |  |

## OperatorPasswordChangeRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `currentPassword` | string | yes |  |
| `newPassword` | string | yes |  |

## OperatorProfileResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `breakGlass` | boolean | no |  |
| `createdAt` | string (date-time) | no |  |
| `displayName` | string | no |  |
| `email` | string | no |  |
| `emailVerified` | boolean | no |  |
| `hasPassword` | boolean | no |  |
| `operatorTier` | string | no |  |
| `platformSuperAdmin` | boolean | no |  |
| `principal` | string | no |  |
| `status` | string | no |  |
| `tenantId` | string (uuid) | no |  |
| `tenantName` | string | no |  |
| `tenantSlug` | string | no |  |
| `userId` | string (uuid) | no |  |
| `username` | string | no |  |

## OperatorProfileUpdateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | string | no |  |
| `username` | string | no |  |

## OptionsRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `email` | string | no |  |
| `tenantSlug` | string | no |  |

## OtpLoginRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `channel` | string | no |  |
| `identifier` | string | yes |  |

## OtpVerifyRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `challengeId` | string | yes |  |
| `channel` | string | no |  |
| `code` | string | yes |  |

## PasskeyRegisterOptionsRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `label` | string | no |  |

## PasskeyRegisterVerifyRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `credential` | string | yes |  |
| `requestId` | string (uuid) | yes |  |

## PasskeyStepRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `mfaToken` | string | no |  |

## PasskeyStepUpRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `credential` | string | no |  |
| `requestId` | string (uuid) | no |  |

## PasskeyStepVerifyRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `credential` | string | no |  |
| `mfaToken` | string | no |  |
| `requestId` | string (uuid) | no |  |

## PermissionCreateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `key` | string | yes |  |

## PermissionDetailResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `action` | string | no |  |
| `applicationId` | string (uuid) | no |  |
| `description` | string | no |  |
| `id` | string (uuid) | no |  |
| `key` | string | no |  |
| `resource` | string | no |  |
| `roleCount` | integer (int32) | no |  |
| `roles` | [RoleRefResponse](13-data-models.md#rolerefresponse)[] | no |  |

## PermissionResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `action` | string | no |  |
| `applicationId` | string (uuid) | no |  |
| `description` | string | no |  |
| `id` | string (uuid) | no |  |
| `key` | string | no |  |
| `resource` | string | no |  |
| `roleCount` | integer (int32) | no |  |

## PermissionUpdateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `key` | string | no |  |

## RecoveryVerifyRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |

## RegisterOptionsRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `label` | string | no |  |

## RegisterVerifyRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `credential` | string | yes |  |
| `requestId` | string (uuid) | yes |  |

## ReplaceUserTenantRolesRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `roleIds` | string (uuid)[] | no |  |

## ResetPasswordOtpRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `challengeId` | string | yes |  |
| `code` | string | yes |  |
| `password` | string | yes |  |

## ResetPasswordRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `password` | string | yes |  |
| `token` | string | yes |  |

## ResetRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `method` | string | no |  |

## Result

| Field | Type | Required | Description |
|---|---|---|---|
| `applied` | boolean | no |  |
| `changes` | [Change](13-data-models.md#change)[] | no |  |
| `dryRun` | boolean | no |  |
| `errors` | string[] | no |  |
| `notes` | string[] | no |  |
| `prune` | boolean | no |  |
| `secrets` | [CreatedSecret](13-data-models.md#createdsecret)[] | no |  |
| `tenant` | string | no |  |

## RoleAssignedUserResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | string | no |  |
| `email` | string | no |  |
| `emailVerified` | boolean | no |  |
| `grantedAt` | string (date-time) | no |  |
| `id` | string (uuid) | no |  |
| `status` | string | no |  |
| `username` | string | no |  |

## RoleCreateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | yes |  |
| `childRoleIds` | string (uuid)[] | no |  |
| `description` | string | no |  |
| `isComposite` | boolean | no |  |
| `name` | string | yes |  |
| `permissionIds` | string (uuid)[] | no |  |
| `tenantId` | string (uuid) | yes |  |

## RoleDetailResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `childRoleIds` | string (uuid)[] | no |  |
| `childRoles` | [RoleRefResponse](13-data-models.md#rolerefresponse)[] | no |  |
| `description` | string | no |  |
| `id` | string (uuid) | no |  |
| `isComposite` | boolean | no |  |
| `isDefault` | boolean | no |  |
| `isSystem` | boolean | no |  |
| `label` | string | no |  |
| `name` | string | no |  |
| `permissionIds` | string (uuid)[] | no |  |
| `permissions` | [PermissionResponse](13-data-models.md#permissionresponse)[] | no |  |
| `tenantId` | string (uuid) | no |  |
| `userCount` | integer (int64) | no |  |

## RolePermissionPatchRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `permissionId` | string (uuid) | yes |  |

## RoleRefResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string (uuid) | no |  |
| `name` | string | no |  |

## RoleSummaryResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `childRoleCount` | integer (int32) | no |  |
| `description` | string | no |  |
| `id` | string (uuid) | no |  |
| `isComposite` | boolean | no |  |
| `isDefault` | boolean | no |  |
| `isSystem` | boolean | no |  |
| `label` | string | no |  |
| `name` | string | no |  |
| `permissionCount` | integer (int32) | no |  |
| `tenantId` | string (uuid) | no |  |
| `userCount` | integer (int64) | no |  |

## RoleUpdateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `childRoleIds` | string (uuid)[] | no |  |
| `description` | string | no |  |
| `isComposite` | boolean | no |  |
| `name` | string | yes |  |
| `permissionIds` | string (uuid)[] | no |  |

## Row

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | string | no |  |
| `email` | string | no |  |
| `grantedAt` | string (date-time) | no |  |
| `grantedBy` | string | no |  |
| `lastLoginAt` | string (date-time) | no |  |
| `status` | string | no |  |
| `userId` | string (uuid) | no |  |

## SeedPermissionsResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `created` | integer (int32) | no |  |
| `totalInCatalog` | integer (int32) | no |  |

## SendCodeRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `factorId` | string (uuid) | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |

## SendRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `factorId` | string (uuid) | no |  |

## SessionLoginRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `email` | string (email) | yes |  |
| `password` | string | yes |  |
| `tenantSlug` | string | no |  |

## SetupCodeConfirmRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |

## SetupSendCodeRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `method` | string | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |
| `phone` | string | no |  |

## SetupTotpConfirmRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |

## SetupWebAuthnRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `kind` | string | no |  |
| `mfaChallengeId` | string (uuid) | yes |  |

## SignupRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | string | no |  |
| `email` | string (email) | yes |  |
| `firstName` | string | no |  |
| `lastName` | string | no |  |
| `password` | string | yes |  |
| `phone` | string | no |  |
| `verificationChannel` | string | no |  |
| `verificationMode` | string | no |  |

## SkipRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `dontAskAgain` | boolean | no |  |
| `mfaChallengeId` | string (uuid) | yes |  |

## StatusesRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `userIds` | string (uuid)[] | no |  |

## StepUpRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no |  |
| `recoveryCode` | string | no |  |

## TenantAdminOperator

| Field | Type | Required | Description |
|---|---|---|---|
| `displayName` | string | no |  |
| `email` | string | no |  |
| `status` | string | no |  |
| `tenantAdminApplicationIds` | string (uuid)[] | no |  |
| `userId` | string (uuid) | no |  |

## TenantConsoleRoleResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `allApplications` | boolean | no |  |
| `applicationScoped` | boolean | no |  |
| `assignmentCount` | integer (int32) | no |  |
| `customized` | boolean | no |  |
| `defaultFeatures` | string[] | no |  |
| `description` | string | no |  |
| `name` | string | no |  |
| `roleType` | string | no |  |

## TenantConsoleRolesCatalogResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `features` | string[] | no |  |
| `roles` | [TenantConsoleRoleResponse](13-data-models.md#tenantconsoleroleresponse)[] | no |  |

## TenantCreateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | yes |  |
| `plan` | string | no |  |
| `slug` | string | no |  |
| `status` | string | no |  |

## TenantEmailRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `email` | string (email) | yes |  |
| `tenantSlug` | string | no |  |

## TenantPermissionCreateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `key` | string | yes |  |

## TenantPermissionResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `description` | string | no |  |
| `id` | string (uuid) | no |  |
| `key` | string | no |  |
| `tenantId` | string (uuid) | no |  |

## TenantResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `appCount` | integer (int64) | no |  |
| `createdAt` | string (date-time) | no |  |
| `id` | string (uuid) | no |  |
| `name` | string | no |  |
| `plan` | string | no |  |
| `slug` | string | no |  |
| `status` | string | no |  |
| `system` | boolean | no |  |
| `userCount` | integer (int64) | no |  |

## TenantRoleCreateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationIds` | string (uuid)[] | no |  |
| `description` | string | no |  |
| `name` | string | yes |  |
| `permissionIds` | string (uuid)[] | no |  |

## TenantRoleDetailResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationIds` | string (uuid)[] | no |  |
| `description` | string | no |  |
| `id` | string (uuid) | no |  |
| `name` | string | no |  |
| `permissionIds` | string (uuid)[] | no |  |
| `permissions` | [TenantPermissionResponse](13-data-models.md#tenantpermissionresponse)[] | no |  |
| `systemRole` | boolean | no |  |
| `tenantId` | string (uuid) | no |  |
| `userCount` | integer (int64) | no |  |

## TenantRoleSummaryResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationScopeCount` | integer (int32) | no |  |
| `description` | string | no |  |
| `id` | string (uuid) | no |  |
| `name` | string | no |  |
| `permissionCount` | integer (int64) | no |  |
| `systemRole` | boolean | no |  |
| `tenantId` | string (uuid) | no |  |
| `userCount` | integer (int64) | no |  |

## TenantRoleUpdateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationIds` | string (uuid)[] | no |  |
| `description` | string | no |  |
| `name` | string | yes |  |
| `permissionIds` | string (uuid)[] | no |  |

## TenantUpdateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | yes |  |
| `plan` | string | no |  |
| `slug` | string | no |  |
| `status` | string | no |  |

## TenantWorkspaceApplication

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string (uuid) | no |  |
| `name` | string | no |  |
| `slug` | string | no |  |
| `status` | string | no |  |
| `userCount` | integer (int64) | no |  |

## TenantWorkspaceApplicationAccess

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `applicationName` | string | no |  |
| `permissionKeys` | string[] | no |  |
| `roleIds` | string (uuid)[] | no |  |
| `roleNames` | string[] | no |  |

## TenantWorkspaceResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationCount` | integer (int32) | no |  |
| `applications` | [TenantWorkspaceApplication](13-data-models.md#tenantworkspaceapplication)[] | no |  |
| `consoleAccess` | [ConsoleAccessAssignment](13-data-models.md#consoleaccessassignment)[] | no |  |
| `plan` | string | no |  |
| `status` | string | no |  |
| `tenantId` | string (uuid) | no |  |
| `tenantName` | string | no |  |
| `tenantSlug` | string | no |  |
| `userCount` | integer (int64) | no |  |
| `users` | [TenantWorkspaceUser](13-data-models.md#tenantworkspaceuser)[] | no |  |

## TenantWorkspaceUser

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationAccess` | [TenantWorkspaceApplicationAccess](13-data-models.md#tenantworkspaceapplicationaccess)[] | no |  |
| `applicationIds` | string (uuid)[] | no |  |
| `consoleFeatureOverrides` | [FeatureOverride](13-data-models.md#featureoverride)[] | no |  |
| `displayName` | string | no |  |
| `effectiveConsoleFeatures` | string[] | no |  |
| `email` | string | no |  |
| `emailVerified` | boolean | no |  |
| `id` | string (uuid) | no |  |
| `onRoster` | boolean | no |  |
| `roleNames` | string[] | no |  |
| `rosterAddedAt` | string (date-time) | no |  |
| `rosterAddedById` | string (uuid) | no |  |
| `rosterAddedByLabel` | string | no |  |
| `rosterSource` | string | no |  |
| `sourceApplicationId` | string (uuid) | no |  |
| `sourceApplicationName` | string | no |  |
| `status` | string | no |  |
| `tenantGovernanceRoleIds` | string (uuid)[] | no |  |
| `tenantGovernanceRoleNames` | string[] | no |  |

## TestEmailRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `bcc` | string[] | no |  |
| `cc` | string[] | no |  |
| `customData` | object | no |  |
| `templateKey` | string | no |  |
| `to` | string (email) | yes |  |

## TestSmsRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `to` | string | no |  |

## TotpConfirmRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no |  |
| `factorId` | string (uuid) | no |  |

## TotpEnrollRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `label` | string | no |  |

## TotpVerifyRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |
| `rememberDevice` | boolean | no |  |

## UpdateApplicationRolesRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `roleIds` | string (uuid)[] | no |  |

## UpdateConsoleRoleFeaturesRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `features` | string[] | no |  |

## UpdateOverridesRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `overrides` | [FeatureOverride](13-data-models.md#featureoverride)[] | no |  |

## UserAuthMethodsUpdateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `methods` | object | no |  |

## UserConsoleCapabilities

| Field | Type | Required | Description |
|---|---|---|---|
| `effectiveFeatures` | string[] | no |  |
| `overrides` | [FeatureOverride](13-data-models.md#featureoverride)[] | no |  |
| `roleDefaults` | string[] | no |  |
| `tenantId` | string (uuid) | no |  |
| `userId` | string (uuid) | no |  |

## UserCreateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `applicationId` | string (uuid) | no |  |
| `email` | string (email) | yes |  |
| `firstName` | string | no |  |
| `lastName` | string | no |  |
| `roleIds` | string (uuid)[] | no |  |
| `status` | string | no |  |
| `tenantId` | string (uuid) | no |  |
| `username` | string | no |  |

## UserResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `allowedAuthMethods` | object | no |  |
| `createdAt` | string (date-time) | no |  |
| `email` | string | no |  |
| `emailVerified` | boolean | no |  |
| `failedLoginCount` | integer (int32) | no |  |
| `firstName` | string | no |  |
| `hasPassword` | boolean | no |  |
| `id` | string (uuid) | no |  |
| `lastLoginAt` | string (date-time) | no |  |
| `lastName` | string | no |  |
| `locked` | boolean | no |  |
| `mfaFactors` | [MfaFactorResponse](13-data-models.md#mfafactorresponse)[] | no |  |
| `phone` | string | no |  |
| `phoneVerified` | boolean | no |  |
| `roleIds` | string[] | no |  |
| `roleNames` | string[] | no |  |
| `status` | string | no |  |
| `tenantId` | string (uuid) | no |  |
| `username` | string | no |  |

## UserTenantRoleAssignmentsResponse

| Field | Type | Required | Description |
|---|---|---|---|
| `roleIds` | string (uuid)[] | no |  |
| `roleNames` | string[] | no |  |
| `userId` | string (uuid) | no |  |

## UserUpdateRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string (email) | yes |  |
| `emailVerified` | boolean | no |  |
| `firstName` | string | no |  |
| `lastName` | string | no |  |
| `roleIds` | string (uuid)[] | no |  |
| `status` | string | no |  |
| `username` | string | no |  |

## VerifyRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | no |  |

## WebAuthnFinishRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `credential` | string | no |  |
| `label` | string | no |  |
| `requestId` | string (uuid) | no |  |

## WebAuthnStartRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `kind` | string | no |  |
| `label` | string | no |  |

## WebAuthnVerifyRequest

| Field | Type | Required | Description |
|---|---|---|---|
| `credential` | string | yes |  |
| `mfaChallengeId` | string (uuid) | yes |  |
| `rememberDevice` | boolean | no |  |
| `requestId` | string (uuid) | yes |  |
