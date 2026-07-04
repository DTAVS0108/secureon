# Verification & SMS settings API validation smoke test
param(
    [string]$AppId = "de2f12ca-3409-4326-a6a7-756231cf7768",
    [string]$BaseUrl = "http://localhost:9000",
    [string]$AdminUser = "admin",
    [string]$AdminPass = "admin"
)

$ErrorActionPreference = "Continue"
$pair = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${AdminUser}:${AdminPass}"))
$adminHeaders = @{ Authorization = "Basic $pair"; "Content-Type" = "application/json" }

$passed = 0
$failed = 0
$warnings = 0

function Assert-Test([string]$Name, [scriptblock]$Block) {
    try {
        & $Block
        Write-Host "  PASS: $Name" -ForegroundColor Green
        $script:passed++
    } catch {
        Write-Host "  FAIL: $Name - $($_.Exception.Message)" -ForegroundColor Red
        $script:failed++
    }
}

function Test-Warn([string]$Name, [scriptblock]$Block) {
    try {
        & $Block
        Write-Host "  WARN: $Name" -ForegroundColor Yellow
        $script:warnings++
    } catch {
        Write-Host "  INFO: $Name - $($_.Exception.Message)" -ForegroundColor Gray
    }
}

Write-Host "`n=== Verification & SMS Settings Test ===" -ForegroundColor Cyan
Write-Host "Base: $BaseUrl | App: $AppId`n"

# --- Platform verification policy ---
Write-Host "[1] Platform verification policy" -ForegroundColor Yellow

$originalPolicy = $null
Assert-Test "GET platform verification-policy" {
    $originalPolicy = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/settings/verification-policy" -Headers $adminHeaders
    if (-not $originalPolicy.signup) { throw "missing signup section" }
    if (-not $originalPolicy.login) { throw "missing login section" }
    if (-not $originalPolicy.recovery) { throw "missing recovery section" }
}

Assert-Test "PUT platform verification-policy roundtrip" {
    $body = @{
        signup = @{
            defaultMode = "otp"
            allowedModes = @("link", "otp")
            requiredChannels = @("email")
            allowUserChannelChoice = $true
            otpLength = 6
            otpTtlSeconds = 600
            maxAttempts = 5
            resendCooldownSeconds = 60
        }
        login = @{
            identifierTypes = @("email", "phone")
            passwordlessModes = @("email_otp", "phone_otp", "magic_link")
            mfaRequired = "optional"
            otpLength = 6
            otpTtlSeconds = 600
            maxAttempts = 5
            resendCooldownSeconds = 60
        }
        recovery = @{
            passwordResetMode = "otp"
            allowedModes = @("link", "otp")
            allowedChannels = @("email", "phone")
            allowUserChannelChoice = $true
            requireVerifiedChannel = $true
            fallbackToLink = $true
            otpLength = 6
            otpTtlSeconds = 600
            maxAttempts = 5
            resendCooldownSeconds = 60
        }
    } | ConvertTo-Json -Depth 6
    $saved = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/settings/verification-policy" -Method PUT -Headers $adminHeaders -Body $body
    if ($saved.signup.defaultMode -ne "otp") { throw "signup.defaultMode not persisted: $($saved.signup.defaultMode)" }
    if ($saved.recovery.passwordResetMode -ne "otp") { throw "recovery.passwordResetMode not persisted" }
}

Assert-Test "OTP numeric fields clamped on save (otpLength 999 -> 8)" {
    $bad = @{ signup = @{ otpLength = 999; otpTtlSeconds = 10; maxAttempts = 1; resendCooldownSeconds = 5 } } | ConvertTo-Json -Depth 4
    $saved = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/settings/verification-policy" -Method PUT -Headers $adminHeaders -Body $bad
    if ($saved.signup.otpLength -ne 8) { throw "otpLength not clamped to 8, got $($saved.signup.otpLength)" }
    if ($saved.signup.otpTtlSeconds -ne 60) { throw "otpTtlSeconds not clamped to 60, got $($saved.signup.otpTtlSeconds)" }
    if ($saved.signup.maxAttempts -ne 3) { throw "maxAttempts not clamped to 3, got $($saved.signup.maxAttempts)" }
    if ($saved.signup.resendCooldownSeconds -ne 30) { throw "resendCooldownSeconds not clamped to 30, got $($saved.signup.resendCooldownSeconds)" }
}

# Restore original-ish policy
if ($originalPolicy) {
    try {
        $restore = @{
            signup = $originalPolicy.signup
            login = $originalPolicy.login
            recovery = $originalPolicy.recovery
        } | ConvertTo-Json -Depth 6
        Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/settings/verification-policy" -Method PUT -Headers $adminHeaders -Body $restore | Out-Null
    } catch { }
}

# --- Platform SMS ---
Write-Host "`n[2] Platform SMS settings" -ForegroundColor Yellow

$originalSms = $null
Assert-Test "GET platform SMS" {
    $originalSms = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/settings/sms" -Headers $adminHeaders
    if (-not $originalSms.provider) { throw "missing provider" }
}

Assert-Test "PUT platform SMS log provider" {
    $body = @{ enabled = $true; provider = "log" } | ConvertTo-Json
    $saved = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/settings/sms" -Method PUT -Headers $adminHeaders -Body $body
    if ($saved.provider -ne "log") { throw "provider not log: $($saved.provider)" }
    if (-not $saved.configured) { throw "log provider should be configured" }
}

Assert-Test "Invalid SMS provider normalizes to log" {
    $body = @{ provider = "invalid_provider_xyz" } | ConvertTo-Json
    $saved = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/settings/sms" -Method PUT -Headers $adminHeaders -Body $body
    if ($saved.provider -ne "log") { throw "expected log, got $($saved.provider)" }
}

Assert-Test "POST platform SMS test (log)" {
    $body = @{ to = "+918851512349" } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/settings/sms/test" -Method POST -Headers $adminHeaders -Body $body
    if ($r.status -ne "sent") { throw "test send failed: $($r | ConvertTo-Json)" }
}

Assert-Test "POST platform SMS test rejects blank to" {
    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/settings/sms/test" -Method POST -Headers $adminHeaders -Body '{"to":""}'
        throw "expected 400 for blank to"
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -ne 400) { throw "expected 400, got $($_.Exception.Response.StatusCode.value__)" }
    }
}

# --- App-scoped settings ---
Write-Host "`n[3] Application-scoped settings" -ForegroundColor Yellow

$appHeaders = $adminHeaders.Clone()
$appHeaders["X-Application-Id"] = $AppId

Assert-Test "GET app verification-policy" {
    $p = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/applications/$AppId/settings/verification-policy" -Headers $adminHeaders
    if (-not $p.scope) { throw "missing scope" }
}

Assert-Test "PUT app verification-policy" {
    $body = @{
        signup = @{ defaultMode = "link"; allowedModes = @("link", "otp") }
        login = @{ identifierTypes = @("email", "phone") }
        recovery = @{ passwordResetMode = "link"; allowedModes = @("link", "otp") }
    } | ConvertTo-Json -Depth 5
    $saved = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/applications/$AppId/settings/verification-policy" -Method PUT -Headers $adminHeaders -Body $body
    if ($saved.signup.defaultMode -ne "link") { throw "app policy not saved" }
}

Assert-Test "GET app SMS settings" {
    $s = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/applications/$AppId/settings/sms" -Headers $adminHeaders
    if (-not $s.availableProviders) { throw "missing availableProviders" }
}

Assert-Test "PUT app SMS log provider" {
    $body = @{ enabled = $true; provider = "log" } | ConvertTo-Json
    $saved = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/applications/$AppId/settings/sms" -Method PUT -Headers $adminHeaders -Body $body
    if ($saved.provider -ne "log") { throw "app sms provider not log" }
}

# --- Feature flags ---
Write-Host "`n[4] Feature flags (recovery_otp, self_service_recovery)" -ForegroundColor Yellow

Assert-Test "GET platform feature flags includes recovery_otp" {
    $flags = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/settings/feature-flags" -Headers $adminHeaders
    $list = if ($flags -is [System.Array]) { $flags } else { @($flags) }
    $recoveryOtp = $list | Where-Object { $_.key -eq "recovery_otp" -or $_.id -eq "recovery_otp" }
    if (-not $recoveryOtp) { throw "recovery_otp flag not found" }
}

Assert-Test "GET app feature flags" {
    $flags = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/applications/$AppId/settings/feature-flags" -Headers $adminHeaders
    $list = if ($flags -is [System.Array]) { $flags } else { @($flags) }
    if ($list.Count -eq 0) { throw "no feature flags returned" }
}

# --- Exposure ---
Write-Host "`n[5] Settings exposure" -ForegroundColor Yellow

Assert-Test "GET app settings exposure includes verification-policy and sms" {
    $exp = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/settings/app-exposure" -Headers $adminHeaders
    if ($null -eq $exp."verification-policy") { throw "verification-policy exposure key missing" }
    if ($null -eq $exp.sms) { throw "sms exposure key missing" }
}

# --- Public manifest reflects policy ---
Write-Host "`n[6] Public manifest" -ForegroundColor Yellow

Assert-Test "GET public application manifest" {
    $m = Invoke-RestMethod -Uri "$BaseUrl/api/v1/applications/$AppId"
    if (-not $m.authMethods -and -not $m.authentication) { throw "manifest missing auth data" }
}

# --- Recovery OTP API validation ---
Write-Host "`n[7] Password recovery API validation" -ForegroundColor Yellow

Assert-Test "Forgot password rejects blank identifier" {
    try {
        $body = @{ identifier = "" } | ConvertTo-Json
        Invoke-RestMethod -Uri "$BaseUrl/api/v1/applications/$AppId/account/password/forgot" -Method POST -ContentType "application/json" -Body $body
        throw "expected error for blank identifier"
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        if ($status -notin 400, 422) { throw "expected 400/422, got $status" }
    }
}

Assert-Test "Forgot password accepts valid request (generic response)" {
    $body = @{ identifier = "nonexistent-$(Get-Random)@example.com"; mode = "link" } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/v1/applications/$AppId/account/password/forgot" -Method POST -ContentType "application/json" -Body $body
    if (-not $r.message) { throw "missing message in response" }
}

Assert-Test "Reset OTP rejects invalid challenge" {
    try {
        $body = @{ challengeId = "00000000-0000-0000-0000-000000000000"; code = "123456"; password = "NewPass123!" } | ConvertTo-Json
        Invoke-RestMethod -Uri "$BaseUrl/api/v1/applications/$AppId/account/password/reset/otp" -Method POST -ContentType "application/json" -Body $body
        throw "expected error for invalid challenge"
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        if ($status -notin 400, 404, 422) { throw "expected 400/404/422, got $status" }
    }
}

# --- Auth methods ---
Write-Host "`n[8] Auth methods (MFA OTP)" -ForegroundColor Yellow

Assert-Test "GET auth methods includes m_email_otp and m_sms" {
    $methods = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/settings/auth-methods" -Headers $adminHeaders
    $list = if ($methods -is [System.Array]) { $methods } else { @($methods) }
    $emailOtp = $list | Where-Object { $_.id -eq "m_email_otp" }
    $smsOtp = $list | Where-Object { $_.id -eq "m_sms" }
    if (-not $emailOtp) { throw "m_email_otp not found" }
    if (-not $smsOtp) { throw "m_sms not found" }
}

# --- Summary ---
Write-Host "`n=== Results ===" -ForegroundColor Cyan
Write-Host "Passed:  $passed" -ForegroundColor Green
Write-Host "Failed:  $failed" -ForegroundColor $(if ($failed -gt 0) { "Red" } else { "Green" })
Write-Host "Warnings: $warnings" -ForegroundColor $(if ($warnings -gt 0) { "Yellow" } else { "Gray" })

if ($failed -gt 0) { exit 1 }
exit 0
