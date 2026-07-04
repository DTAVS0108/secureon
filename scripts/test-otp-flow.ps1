# OTP flow smoke test - reads codes from MailHog (email) and auth-server log (SMS)
param(
    [string]$AppId = "de2f12ca-3409-4326-a6a7-756231cf7768",
    [string]$Email = "dmtyagi1997@gmail.com",
    [string]$Phone = "+918851512349",
    [string]$BaseUrl = "http://localhost:9000",
    [string]$MailHogUrl = "http://localhost:8025",
    [string]$LogFile = "",
    [int]$TimeoutSec = 15
)

$ErrorActionPreference = "Stop"
if (-not $LogFile) {
    $LogFile = Join-Path (Split-Path $PSScriptRoot -Parent) ".local\logs\auth-server.log"
}

function Get-LatestOtpFromMailHog([string]$toEmail) {
    $msgs = Invoke-RestMethod -Uri "$MailHogUrl/api/v2/messages?limit=50"
    foreach ($item in $msgs.items) {
        $to = ($item.Content.Headers.To -join " ")
        if ($to -notmatch [regex]::Escape($toEmail)) { continue }
        $body = $item.Content.Body
        if ($body -match 'code is:\s*(\d{4,8})') { return $Matches[1] }
        if ($body -match '(?m)(\d{6})') { return $Matches[1] }
    }
    return $null
}

function Get-LatestOtpFromSmsLog([string]$phone) {
    if (-not (Test-Path $LogFile)) { return $null }
    $lines = Get-Content $LogFile -Tail 200 -ErrorAction SilentlyContinue
    $pattern = "SMS to $([regex]::Escape($phone)):"
    foreach ($line in ($lines | Select-Object -Last 50)) {
        if ($line -match $pattern -and $line -match 'code is:\s*(\d{4,8})') {
            return $Matches[1]
        }
    }
    return $null
}

function Get-LatestChallengeId([string]$destination) {
    $needle = $destination.Replace("+", "")
    $sql = "SELECT id::text FROM platform.otp_challenge WHERE consumed_at IS NULL AND expires_at > now() AND destination ILIKE '%$needle%' ORDER BY created_at DESC LIMIT 1;"
    $id = docker exec secureone-postgres psql -U secureone -d secureone -t -A -c $sql 2>$null
    return ($id | ForEach-Object { $_.Trim() }) | Where-Object { $_ } | Select-Object -First 1
}

function Enable-SmsOtp {
    $pair = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:admin"))
    $h = @{ Authorization = "Basic $pair"; "Content-Type" = "application/json" }
    $methods = Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/settings/auth-methods" -Headers $h
    $list = @($methods.value)
    if ($list.Count -eq 0) { $list = @($methods) }
    $changed = $false
    foreach ($m in $list) {
        if ($m.id -eq "m_sms" -and -not $m.enabled) { $m.enabled = $true; $changed = $true }
        if ($m.id -eq "m_email_otp" -and -not $m.enabled) { $m.enabled = $true; $changed = $true }
    }
    if ($changed) {
        Invoke-RestMethod -Uri "$BaseUrl/api/admin/v1/settings/auth-methods" -Method PUT -Headers $h -Body ($list | ConvertTo-Json -Depth 6) | Out-Null
        Write-Host "  Enabled m_sms and m_email_otp" -ForegroundColor Green
    } else {
        Write-Host "  m_sms / m_email_otp already enabled" -ForegroundColor Gray
    }
}

Write-Host "`n=== OTP Flow Test ===" -ForegroundColor Cyan
Write-Host "App: $AppId | Email: $Email | Phone: $Phone`n"

Write-Host "[1] Enabling SMS + Email OTP auth methods..." -ForegroundColor Yellow
Enable-SmsOtp

# --- Email login OTP ---
Write-Host "`n[2] Email login OTP request..." -ForegroundColor Yellow
$body = @{ identifier = $Email; channel = "email" } | ConvertTo-Json
try {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/v1/applications/$AppId/auth/otp/request" -Method POST -ContentType "application/json" -Body $body
    Write-Host "  Response: $($r.message)"
} catch {
    Write-Host "  FAILED: $($_.Exception.Message)" -ForegroundColor Red
}

Start-Sleep -Seconds 2
$emailCode = Get-LatestOtpFromMailHog $Email
$emailChallenge = Get-LatestChallengeId $Email
Write-Host "  MailHog OTP: $(if ($emailCode) { $emailCode } else { 'NOT FOUND' })"
Write-Host "  Challenge ID: $(if ($emailChallenge) { $emailChallenge } else { 'NOT FOUND' })"

if ($emailCode -and $emailChallenge) {
    $verifyBody = @{ challengeId = $emailChallenge; code = $emailCode } | ConvertTo-Json
    try {
        $session = Invoke-WebRequest -Uri "$BaseUrl/api/v1/applications/$AppId/auth/otp/verify" -Method POST -ContentType "application/json" -Body $verifyBody -SessionVariable otpSession -TimeoutSec $TimeoutSec -UseBasicParsing
        if ($session.StatusCode -eq 204) {
            Write-Host "  Email login OTP verify: SUCCESS (204 + session)" -ForegroundColor Green
        }
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        Write-Host "  Email login OTP verify FAILED ($status): $($_.ErrorDetails.Message)" -ForegroundColor Red
    }
} else {
    Write-Host "  Skipping email verify - missing code or challenge" -ForegroundColor Red
}

# --- Ensure phone on user for SMS test ---
Write-Host "`n[3] Attaching phone to user for SMS OTP test..." -ForegroundColor Yellow
$userId = docker exec secureone-postgres psql -U secureone -d secureone -t -A -c "SELECT id::text FROM platform.user_account WHERE email = '$Email' LIMIT 1;"
$userId = ($userId | ForEach-Object { $_.Trim() }) | Where-Object { $_ } | Select-Object -First 1
if ($userId) {
    $updateSql = "UPDATE platform.user_account SET phone = '$Phone', phone_verified = false WHERE id = '$userId';"
    docker exec secureone-postgres psql -U secureone -d secureone -c $updateSql | Out-Null
    Write-Host "  Phone $Phone set on user $userId"
} else {
    Write-Host "  User not found - will try signup with phone" -ForegroundColor Red
}

# --- SMS login OTP ---
Write-Host "`n[4] SMS login OTP request..." -ForegroundColor Yellow
$smsBody = @{ identifier = $Phone; channel = "sms" } | ConvertTo-Json
try {
    $r2 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/applications/$AppId/auth/otp/request" -Method POST -ContentType "application/json" -Body $smsBody
    Write-Host "  Response: $($r2.message)"
} catch {
    Write-Host "  FAILED: $($_.Exception.Message)" -ForegroundColor Red
}

Start-Sleep -Seconds 2
$smsCode = Get-LatestOtpFromSmsLog $Phone
$smsChallenge = Get-LatestChallengeId $Phone
Write-Host "  SMS log OTP: $(if ($smsCode) { $smsCode } else { 'NOT FOUND - check auth-server.log' })"
Write-Host "  Challenge ID: $(if ($smsChallenge) { $smsChallenge } else { 'NOT FOUND' })"

if ($smsCode -and $smsChallenge) {
    $verifySms = @{ challengeId = $smsChallenge; code = $smsCode } | ConvertTo-Json
    try {
        $session2 = Invoke-WebRequest -Uri "$BaseUrl/api/v1/applications/$AppId/auth/otp/verify" -Method POST -ContentType "application/json" -Body $verifySms -TimeoutSec $TimeoutSec -UseBasicParsing
        if ($session2.StatusCode -eq 204) {
            Write-Host "  SMS login OTP verify: SUCCESS (204 + session)" -ForegroundColor Green
        }
    } catch {
        Write-Host "  SMS login OTP verify FAILED: $($_.ErrorDetails.Message)" -ForegroundColor Red
    }
} else {
    Write-Host "  Skipping SMS verify - missing code or challenge" -ForegroundColor Red
}

# --- Signup email OTP (new user) ---
Write-Host "`n[5] Signup with email OTP (test user)..." -ForegroundColor Yellow
$testEmail = "otp-test-$(Get-Random -Maximum 99999)@example.com"
$signupBody = @{
    email = $testEmail
    password = "TestPass123!"
    verificationMode = "otp"
    verificationChannel = "email"
} | ConvertTo-Json
try {
    $signup = Invoke-RestMethod -Uri "$BaseUrl/api/v1/applications/$AppId/signup" -Method POST -ContentType "application/json" -Body $signupBody
    Write-Host "  Signup: $($signup.message)"
    Write-Host "  Challenge: $($signup.challengeId)"
    Start-Sleep -Seconds 2
    $signupCode = Get-LatestOtpFromMailHog $testEmail
    Write-Host "  MailHog OTP: $(if ($signupCode) { $signupCode } else { 'NOT FOUND' })"
    if ($signup.challengeId -and $signupCode) {
        $confirm = @{ challengeId = $signup.challengeId; code = $signupCode } | ConvertTo-Json
        $verified = Invoke-RestMethod -Uri "$BaseUrl/api/v1/applications/$AppId/account/verify/otp/confirm" -Method POST -ContentType "application/json" -Body $confirm
        Write-Host "  Signup email OTP confirm: verified=$($verified.verified)" -ForegroundColor Green
    }
} catch {
    Write-Host "  Signup email OTP FAILED: $($_.ErrorDetails.Message)" -ForegroundColor Red
}

# --- Signup phone OTP (new user) ---
Write-Host "`n[6] Signup with phone OTP (test user)..." -ForegroundColor Yellow
$testPhone = "+91885" + (Get-Random -Minimum 1000000 -Maximum 9999999)
$testPhoneEmail = "phone-otp-$(Get-Random -Maximum 99999)@example.com"
$signupPhoneBody = @{
    email = $testPhoneEmail
    password = "TestPass123!"
    phone = $testPhone
    verificationMode = "otp"
    verificationChannel = "sms"
} | ConvertTo-Json
try {
    $signupP = Invoke-RestMethod -Uri "$BaseUrl/api/v1/applications/$AppId/signup" -Method POST -ContentType "application/json" -Body $signupPhoneBody
    Write-Host "  Signup: $($signupP.message)"
    Write-Host "  Challenge: $($signupP.challengeId) channel=$($signupP.channel)"
    Start-Sleep -Seconds 2
    $signupPhoneCode = Get-LatestOtpFromSmsLog $testPhone
    Write-Host "  SMS log OTP: $(if ($signupPhoneCode) { $signupPhoneCode } else { 'NOT FOUND' })"
    if ($signupP.challengeId -and $signupPhoneCode) {
        $confirmP = @{ challengeId = $signupP.challengeId; code = $signupPhoneCode } | ConvertTo-Json
        # Phone confirm needs channel=phone via global API; app endpoint hardcodes email — use global
        $confirmPGlobal = @{ challengeId = $signupP.challengeId; code = $signupPhoneCode; channel = "phone" } | ConvertTo-Json
        $verifiedP = Invoke-RestMethod -Uri "$BaseUrl/api/v1/account/verify/otp/confirm" -Method POST -ContentType "application/json" -Body $confirmPGlobal
        Write-Host "  Signup phone OTP confirm: verified=$($verifiedP.verified)" -ForegroundColor Green
    }
} catch {
    Write-Host "  Signup phone OTP FAILED: $($_.ErrorDetails.Message)" -ForegroundColor Red
}

Write-Host "`n=== Done ===" -ForegroundColor Cyan
Write-Host "Email inbox (MailHog): $MailHogUrl"
Write-Host "SMS codes (dev): logged in $LogFile"
