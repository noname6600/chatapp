@echo off
REM start-services-local.bat
REM Launches chatappBE backend services with the local Spring profile.
REM Delegates to start-services-local.ps1 via PowerShell.
REM
REM Usage (from chatappBE root):
REM   scripts\start-services-local.bat              -> starts all 8 services
REM   scripts\start-services-local.bat -Auth -Gateway -> starts auth + gateway only
REM
REM Switches: -All -Auth -User -Chat -Presence -Friendship -Notification -Upload -Gateway

powershell -ExecutionPolicy Bypass -File "%~dp0start-services-local.ps1" %*
