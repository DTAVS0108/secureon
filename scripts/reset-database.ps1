# Wipe local Postgres data and restart with a clean schema.
# Usage:  .\scripts\reset-database.ps1
#         .\scripts\reset-database.ps1 -SkipInstall

param(
    [switch]$SkipInstall
)

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ComposeFile = Join-Path $RepoRoot "deploy\docker-compose.yml"

Write-Host "Resetting SecureOne local database (all tenants, users, and apps will be removed)..." -ForegroundColor Yellow

& (Join-Path $RepoRoot "scripts\stop-all.ps1") -KeepDocker

Push-Location $RepoRoot
try {
    docker compose -f $ComposeFile down -v
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose down -v failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Write-Host "Database volume removed. Starting fresh stack..." -ForegroundColor Cyan

$startArgs = @{ SkipInstall = $SkipInstall }
& (Join-Path $RepoRoot "scripts\start-all.ps1") @startArgs

Write-Host ""
Write-Host "Clean database ready." -ForegroundColor Green
Write-Host "  Platform admin: username admin, password from SECUREONE_DEV_PASSWORD (default admin), no tenant slug."
Write-Host "  Create tenants, applications, users, grant Admin console access, set passwords, then test tenant login."
