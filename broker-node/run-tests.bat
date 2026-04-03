@echo off
echo ============================================
echo   OFFLOAD-HPC Integration Test
echo ============================================
echo.

cd /d "%~dp0"

set HOST=%~1
set PORT=%~2
if "%HOST%"=="" set HOST=127.0.0.1
if "%PORT%"=="" set PORT=9000

echo Compiling test client...
javac IntegrationTest.java
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed!
    pause
    exit /b 1
)

echo Running tests against Broker at %HOST%:%PORT%...
echo.

java IntegrationTest %HOST% %PORT%

echo.
pause
