<#
.SYNOPSIS
  Start one or more chatappBE backend services with the local Spring profile.

.DESCRIPTION
  Launches each selected service in a separate PowerShell window via:
    gradlew :xxx-service:bootRun --args=--spring.profiles.active=local
  Run from chatappBE root, or the script auto-resolves the path.

.PARAMETER All
  Start all 8 backend services (default when no switch is given).

.PARAMETER Auth
  Start auth-service (port 8081).

.PARAMETER User
  Start user-service (port 8082).

.PARAMETER Chat
  Start chat-service (port 8083).

.PARAMETER Presence
  Start presence-service (port 8084).

.PARAMETER Friendship
  Start friendship-service (port 8085).

.PARAMETER Notification
  Start notification-service (port 8086).

.PARAMETER Upload
  Start upload-service (port 8088).

.PARAMETER Gateway
  Start gateway-service (port 8080).

.EXAMPLE
  .\scripts\start-services-local.ps1
  # Starts all 8 services

.EXAMPLE
  .\scripts\start-services-local.ps1 -Auth -Gateway
  # Starts only auth-service and gateway-service

.EXAMPLE
  .\scripts\start-services-local.ps1 -Chat -User -Friendship
  # Starts chat, user, and friendship services
#>

[CmdletBinding()]
param(
    [switch]$All,
    [switch]$Auth,
    [switch]$User,
    [switch]$Chat,
    [switch]$Presence,
    [switch]$Friendship,
    [switch]$Notification,
    [switch]$Upload,
    [switch]$Gateway
)

# Resolve chatappBE root regardless of where the script is invoked from
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$chatappBERoot = Split-Path -Parent $scriptDir

# If no specific service selected, default to All
$anySelected = $Auth -or $User -or $Chat -or $Presence -or $Friendship -or $Notification -or $Upload -or $Gateway
if (-not $anySelected) {
    $All = $true
}

# Service map: display name -> gradle task name
$services = [ordered]@{
    'auth-service'         = 'auth-service'
    'user-service'         = 'user-service'
    'chat-service'         = 'chat-service'
    'presence-service'     = 'presence-service'
    'friendship-service'   = 'friendship-service'
    'notification-service' = 'notification-service'
    'upload-service'       = 'upload-service'
    'gateway-service'      = 'gateway-service'
}

# Build list of services to start
$toStart = @()
if ($All -or $Auth)         { $toStart += 'auth-service' }
if ($All -or $User)         { $toStart += 'user-service' }
if ($All -or $Chat)         { $toStart += 'chat-service' }
if ($All -or $Presence)     { $toStart += 'presence-service' }
if ($All -or $Friendship)   { $toStart += 'friendship-service' }
if ($All -or $Notification) { $toStart += 'notification-service' }
if ($All -or $Upload)       { $toStart += 'upload-service' }
if ($All -or $Gateway)      { $toStart += 'gateway-service' }

Write-Host ""
Write-Host "=== chatappBE Local Service Launcher ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "REMINDER: Docker infra must be running before starting services." -ForegroundColor Yellow
Write-Host "  Command: docker compose -f docker-compose.local.yml up -d auth-db user-db chat-db friendship-db notification-db redis zookeeper kafka" -ForegroundColor Yellow
Write-Host ""
Write-Host "Starting $($toStart.Count) service(s) from: $chatappBERoot" -ForegroundColor Cyan
Write-Host ""

foreach ($svc in $toStart) {
    $gradleTask = ":${svc}:bootRun"
    $args = "--args=--spring.profiles.active=local"
    $windowTitle = $svc

    # Build the command string that will run inside the new window
    $cmd = "Set-Location '$chatappBERoot'; `$host.UI.RawUI.WindowTitle = '$windowTitle'; Write-Host 'Starting $svc...' -ForegroundColor Green; .\gradlew $gradleTask $args; Read-Host 'Process exited. Press Enter to close'"

    Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd

    Write-Host "  Launched: $svc" -ForegroundColor Green
    Start-Sleep -Milliseconds 300
}

Write-Host ""
Write-Host "All selected services launched. Check the individual windows for startup logs." -ForegroundColor Cyan
Write-Host ""
