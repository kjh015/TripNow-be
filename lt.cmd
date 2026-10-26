@echo off
rem k6 load test shortcut. No setup needed. See load-test/README.md
rem   PowerShell: .\lt hang      cmd: lt hang
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0load-test\run.ps1" %*
exit /b %ERRORLEVEL%
