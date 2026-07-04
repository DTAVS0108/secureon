# Master platform smoke test — chains health checks and existing script suites.
# Requires auth-server on :9000 (and optionally MailHog :8025, Postgres for OTP DB lookups).
param(
    [string]$BaseUrl = "http://localhost:9000",
    [string]$AppId = "de2f12ca-3409-4326-a6a7-756231cf7768",
    [switch]$SkipOtp,
    [switch]$SkipOAuth,
    [switch]$SkipE2e
)

$ErrorActionPreference = "Continue"
$scriptRoot = $PSScriptRoot
$script:pass = 0
$script:fail = 0
$script:skip = 0

function Record([string]$Name, [string]$Result, [string]$Detail = "") {
    switch ($Result) {
        "PASS" {
            $script:pass++
            Write-Host "[PASS] $Name" -ForegroundColor Green
        }
        "FAIL" {
            $script:fail++
            Write-Host "[FAIL] $Name" -ForegroundColor Red
            if ($Detail) { Write-Host "       $Detail" -ForegroundColor DarkRed }
        }
        "SKIP" {
            $script:skip++
            Write-Host "[SKIP] $Name" -ForegroundColor Yellow
            if ($Detail) { Write-Host "       $Detail" -ForegroundColor DarkYellow }
        }
    }
}

function Invoke-ScriptSuite([string]$Name, [string]$Path, [string[]]$Args = @()) {
    if (-not (Test-Path $Path)) {
        Record $Name "FAIL" "Script not found: $Path"
        return
    }
    Write-Host "`n--- $Name ---" -ForegroundColor Cyan
    & $Path @Args
    if ($LASTEXITCODE -eq 0 -or $null -eq $LASTEXITCODE) {
        Record $Name "PASS"
    } else {
        Record $Name "FAIL" "exit code $LASTEXITCODE"
    }
}

function New-Session([string]$User, [string]$Pass) {
    $sess = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $cookie = Join-Path $env:TEMP "secureone-platform-$([Guid]::NewGuid().ToString('N')).txt"
    try {
        $code = curl.exe -s -o NUL -w "%{http_code}" -c $cookie -b $cookie -X POST "$BaseUrl/login" `
            -d "username=$([uri]::EscapeDataString($User))&password=$([uri]::EscapeDataString($Pass))" --max-time 15
        if ($code -ne "302" -and $code -ne "200") {
            return $null
        }
        foreach ($line in Get-Content $cookie -ErrorAction SilentlyContinue) {
            if ($line -match '^\S+\s+(\S+)\s+(\S+)') {
                $sess.Cookies.Add((New-Object System.Net.Cookie($Matches[2], "", "/", "localhost")))
            }
        }
        return $sess
    } finally {
        Remove-Item $cookie -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "`n=== SecureOne Full Platform Smoke Test ===" -ForegroundColor Cyan
Write-Host "Base: $BaseUrl | App: $AppId`n"

# 1. Health + manifest
Write-Host "--- Health & manifest ---" -ForegroundColor Cyan
try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 10
    if ($health.status -eq "UP") { Record "actuator/health" "PASS" } else { Record "actuator/health" "FAIL" $health.status }
} catch {
    Record "actuator/health" "FAIL" $_.Exception.Message
}

try {
    $info = Invoke-RestMethod -Uri "$BaseUrl/api/info" -TimeoutSec 10
    if ($info.environment) { Record "api/info environment" "PASS" } else { Record "api/info environment" "FAIL" "missing environment" }
} catch {
    Record "api/info" "FAIL" $_.Exception.Message
}

try {
    $manifest = Invoke-RestMethod -Uri "$BaseUrl/api/v1/applications/$AppId" -TimeoutSec 10
    if ($manifest.id -or $manifest.applicationId) { Record "public manifest" "PASS" } else { Record "public manifest" "FAIL" }
} catch {
    Record "public manifest" "FAIL" $_.Exception.Message
}

try {
    $fed = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/federation/providers?applicationId=$AppId" -TimeoutSec 10
    if ($null -ne $fed) { Record "federation providers API" "PASS" } else { Record "federation providers API" "FAIL" }
} catch {
    Record "federation providers API" "FAIL" $_.Exception.Message
}

try {
    $pk = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/passkey/options" -Method POST `
        -ContentType "application/json" -Body (@{ applicationId = $AppId } | ConvertTo-Json) -TimeoutSec 10
    if ($pk.requestId) { Record "passkey options API" "PASS" } else { Record "passkey options API" "FAIL" "missing requestId" }
} catch {
    Record "passkey options API" "FAIL" $_.Exception.Message
}

# 2. Verification settings
Invoke-ScriptSuite "Verification settings" (Join-Path $scriptRoot "test-verification-settings.ps1") @(
    "-BaseUrl", $BaseUrl,
    "-AppId", $AppId
)

# 3. OTP flow
if ($SkipOtp) {
    Record "OTP flow" "SKIP" "-SkipOtp"
} else {
    Invoke-ScriptSuite "OTP flow" (Join-Path $scriptRoot "test-otp-flow.ps1") @(
        "-BaseUrl", $BaseUrl,
        "-AppId", $AppId
    )
}

# 4. External client manifest
Invoke-ScriptSuite "External client manifest" (Join-Path $scriptRoot "test-external-client.ps1") @("-Base", $BaseUrl)

# 5. OAuth PKCE token exchange
if ($SkipOAuth) {
    Record "OAuth PKCE" "SKIP" "-SkipOAuth"
} else {
    Invoke-ScriptSuite "OAuth PKCE" (Join-Path $scriptRoot "test-oauth-token.ps1") @("-Base", $BaseUrl)
}

# 6. Session list / revoke API
Write-Host "`n--- Session revoke API ---" -ForegroundColor Cyan
$sess = New-Session "acme:sarah.chen@acme.com" "SecureOne123!"
if ($null -eq $sess) {
    Record "session login" "FAIL" "form login did not succeed"
} else {
    try {
        $me = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/me" -WebSession $sess -TimeoutSec 10
        if ($me.authenticated) { Record "session /auth/me" "PASS" } else { Record "session /auth/me" "FAIL" }
    } catch {
        Record "session /auth/me" "FAIL" $_.Exception.Message
    }
    try {
        $list = Invoke-RestMethod -Uri "$BaseUrl/api/v1/account/sessions" -WebSession $sess -TimeoutSec 10
        if ($list.sessions -and $list.sessions.Count -ge 1) {
            Record "GET /account/sessions" "PASS" "$($list.sessions.Count) session(s)"
        } else {
            Record "GET /account/sessions" "FAIL" "no sessions returned"
        }
    } catch {
        Record "GET /account/sessions" "FAIL" $_.Exception.Message
    }
}

# 7. Auth E2E (optional — longer suite)
if ($SkipE2e) {
    Record "Auth E2E suite" "SKIP" "-SkipE2e"
} else {
    Invoke-ScriptSuite "Auth E2E suite" (Join-Path $scriptRoot "test-auth-e2e.ps1") @("-Base", $BaseUrl)
}

Write-Host "`n=== Summary: $($script:pass) passed, $($script:fail) failed, $($script:skip) skipped ===" -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 }
exit 0
