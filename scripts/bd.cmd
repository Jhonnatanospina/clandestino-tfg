@echo off
rem Atajo para scripts\bd.ps1 (cargar o probar) sin tocar la política de PowerShell.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0bd.ps1" %*
