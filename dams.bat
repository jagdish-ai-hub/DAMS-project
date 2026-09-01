@echo off
setlocal EnableDelayedExpansion
REM ============================================================================
REM  dams.bat — restart the DAMS backend + frontend for local dev.
REM    backend : Spring Boot on http://localhost:8080  (profile "local", Neon DB)
REM    frontend: Vite on        http://localhost:2314
REM  Each runs in its own window; close the window or press Ctrl+C to stop it.
REM  Re-run this file any time to kill both and start fresh.
REM ============================================================================

set "ROOT=%~dp0"
set "MVN=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.2.4\plugins\maven\lib\maven3\bin\mvn.cmd"
set "BACKEND_PORT=8080"
set "FRONTEND_PORT=2314"

if not exist "%ROOT%.env" (
  echo [dams] ERROR: %ROOT%.env not found. Copy .env.example to .env and fill it in.
  exit /b 1
)
if not exist "%MVN%" (
  echo [dams] ERROR: Maven not found at:
  echo         %MVN%
  echo         Edit the MVN=... line in dams.bat to point at your mvn.cmd.
  exit /b 1
)

echo [dams] Stopping anything on ports %BACKEND_PORT% and %FRONTEND_PORT% ...
powershell -NoProfile -Command ^
  "Get-NetTCPConnection -LocalPort %BACKEND_PORT%,%FRONTEND_PORT% -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object { try { Stop-Process -Id $_ -Force -ErrorAction Stop; Write-Host ('  killed PID ' + $_) } catch {} }"
timeout /t 2 /nobreak >nul

echo [dams] Loading environment from .env ...
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ROOT%.env") do (
  set "%%A=%%B"
)

echo [dams] Starting backend  (http://localhost:%BACKEND_PORT%) ...
start "DAMS backend" cmd /k "cd /d "%ROOT%backend" && "%MVN%" -o -q spring-boot:run -Dspring-boot.run.profiles=local"

echo [dams] Starting frontend (http://localhost:%FRONTEND_PORT%) ...
start "DAMS frontend" cmd /k "cd /d "%ROOT%frontend" && npx vite --port %FRONTEND_PORT% --strictPort --host"

echo.
echo [dams] Both starting in their own windows.
echo        App        : http://localhost:%FRONTEND_PORT%
echo        API        : http://localhost:%BACKEND_PORT%/api/v1
echo        API docs   : http://localhost:%BACKEND_PORT%/swagger-ui.html
echo        Backend takes ~30-40s to be ready (Flyway + Neon).
echo.
endlocal
