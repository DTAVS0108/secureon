# Start SecureOne local dev stack: Docker (Postgres, Redis, MailHog) + auth-server + admin-web.
# Usage:  .\scripts\start-all.ps1
# Stop:   .\scripts\stop-all.ps1

param(
    [switch]$SkipDocker,
    [switch]$SkipInstall,
    [switch]$NoWait
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ComposeFile = Join-Path $RepoRoot "deploy\docker-compose.yml"
$AuthDir = Join-Path $RepoRoot "apps\auth-server"
$AdminDir = Join-Path $RepoRoot "apps\admin-web"
$LocalDir = Join-Path $RepoRoot ".local"
$LogsDir = Join-Path $LocalDir "logs"
$PidsDir = Join-Path $LocalDir "pids"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Test-Command([string]$Name) {
    $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Test-DockerDaemon {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        & docker info *> $null
        return $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Ensure-DockerDaemon([int]$TimeoutSec = 120) {
    if (Test-DockerDaemon) {
        return $true
    }

    $candidates = @(
        "${env:ProgramFiles}\Docker\Docker\Docker Desktop.exe",
        "${env:ProgramFiles(x86)}\Docker\Docker\Docker Desktop.exe"
    )
    foreach ($exe in $candidates) {
        if (Test-Path $exe) {
            Write-Host "  Docker daemon is not running. Starting Docker Desktop..."
            Start-Process -FilePath $exe | Out-Null
            $deadline = (Get-Date).AddSeconds($TimeoutSec)
            while ((Get-Date) -lt $deadline) {
                Start-Sleep -Seconds 3
                if (Test-DockerDaemon) {
                    Write-Host "  Docker daemon is ready."
                    return $true
                }
            }
            break
        }
    }
    return $false
}

function Wait-DockerHealthy([string]$Container, [int]$TimeoutSec = 90) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $health = docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{end}}" $Container 2>$null
        if ($health -eq "healthy") {
            return $true
        }
        if (-not $health) {
            $running = docker inspect --format "{{.State.Running}}" $Container 2>$null
            if ($running -eq "true") {
                return $true
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for container '$Container' to become healthy."
}

function Wait-HttpOk([string]$Url, [int]$TimeoutSec = 180) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $res = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($res.StatusCode -ge 200 -and $res.StatusCode -lt 500) {
                return $true
            }
        } catch {
            # not ready yet
        }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Start-BackgroundService {
    param(
        [string]$Name,
        [string]$WorkingDirectory,
        [string]$Command,
        [string]$LogFile,
        [string]$PidFile
    )

    if (Test-Path $PidFile) {
        $oldPid = Get-Content $PidFile -ErrorAction SilentlyContinue
        if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
            Write-Host "  $Name already running (PID $oldPid). Skipping start." -ForegroundColor Yellow
            return [int]$oldPid
        }
    }

    $escapedCmd = $Command.Replace("'", "''")
    $proc = Start-Process -FilePath "powershell.exe" `
        -ArgumentList @(
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-Command",
            "Set-Location -LiteralPath '$WorkingDirectory'; $escapedCmd *>&1 | Tee-Object -FilePath '$LogFile'"
        ) `
        -PassThru `
        -WindowStyle Minimized

    Set-Content -Path $PidFile -Value $proc.Id -Encoding ascii
    Write-Host "  Started $Name (PID $($proc.Id)). Log: $LogFile"
    return $proc.Id
}

New-Item -ItemType Directory -Force -Path $LogsDir, $PidsDir | Out-Null

Write-Host "SecureOne - starting local stack" -ForegroundColor Green
Write-Host "Repo: $RepoRoot"

if (-not $SkipDocker) {
    if (-not (Test-Command "docker")) {
        throw "Docker is not installed or not on PATH. Install Docker Desktop or pass -SkipDocker."
    }
    if (-not (Ensure-DockerDaemon)) {
        throw @"
Docker is installed but the daemon is not running.
Start Docker Desktop manually, then run this script again.
Or pass -SkipDocker if Postgres/Redis/MailHog are already running elsewhere.
"@
    }
    Write-Step 'Starting Docker services (Postgres, Redis, MailHog)'
    docker compose -f $ComposeFile up -d
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed. Is Docker Desktop running?"
    }
    Wait-DockerHealthy "secureone-postgres" | Out-Null
    Wait-DockerHealthy "secureone-redis" | Out-Null
    Write-Host "  Docker services are up."
} else {
    Write-Step "Skipping Docker (-SkipDocker)"
}

if (-not $SkipInstall) {
    Write-Step 'Installing JS dependencies (pnpm)'
    Push-Location $RepoRoot
    try {
        if (-not (Test-Command "pnpm")) {
            throw "pnpm is not on PATH. Install Node 20+ and run: corepack enable"
        }
        pnpm install
        if ($LASTEXITCODE -ne 0) {
            throw "pnpm install failed."
        }
    } finally {
        Pop-Location
    }
}

$envLocal = Join-Path $AdminDir ".env.local"
$envExample = Join-Path $AdminDir ".env.local.example"
if (-not (Test-Path $envLocal) -and (Test-Path $envExample)) {
    Copy-Item $envExample $envLocal
    Write-Host "  Created apps/admin-web/.env.local from example."
}

Write-Step 'Starting auth-server on port 9000'
# The auth-server serves the console at /admin by forwarding to `next dev` (one URL, as in production).
if (-not $env:SECUREONE_CONSOLE_UPSTREAM_URL) { $env:SECUREONE_CONSOLE_UPSTREAM_URL = "http://localhost:3001" }
$authPid = Start-BackgroundService `
    -Name "auth-server" `
    -WorkingDirectory $AuthDir `
    -Command ".\gradlew.bat bootRun --no-daemon" `
    -LogFile (Join-Path $LogsDir "auth-server.log") `
    -PidFile (Join-Path $PidsDir "auth-server.pid")

Write-Step 'Starting admin-web on port 3001'
$adminPid = Start-BackgroundService `
    -Name "admin-web" `
    -WorkingDirectory $RepoRoot `
    -Command "pnpm --filter admin-web dev" `
    -LogFile (Join-Path $LogsDir "admin-web.log") `
    -PidFile (Join-Path $PidsDir "admin-web.pid")

if (-not $NoWait) {
    Write-Step "Waiting for services to respond"
    $authOk = Wait-HttpOk "http://localhost:9000/api/info" 240
    $adminOk = Wait-HttpOk "http://localhost:3001/admin/login" 120
    if (-not $authOk) {
        Write-Host "  auth-server did not respond on :9000 yet. Check .local/logs/auth-server.log" -ForegroundColor Yellow
    }
    if (-not $adminOk) {
        Write-Host "  admin-web did not respond on :3001 yet. Check .local/logs/admin-web.log" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "SecureOne stack started." -ForegroundColor Green
Write-Host ""
Write-Host "  Auth server     http://localhost:9000"
Write-Host "  API docs        http://localhost:9000/docs"
Write-Host "  Admin console   http://localhost:9000/admin   (dev server with hot reload: http://localhost:3001/admin)"
Write-Host "  MailHog UI      http://localhost:8025"
Write-Host '  Postgres        localhost:5432  user/pass: secureone / secureone'
Write-Host "  Redis           localhost:6379"
Write-Host ""
Write-Host "  Logs            $LogsDir"
Write-Host "  Stop everything .\scripts\stop-all.ps1"
Write-Host ""
