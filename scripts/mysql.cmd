@echo off
rem Atajo para scripts\mysql.ps1 (iniciar, parar o estado) sin tocar la política de PowerShell.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0mysql.ps1" %*
