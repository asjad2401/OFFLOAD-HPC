@echo off
echo ============================================
echo   OFFLOAD-HPC Grid Node
echo ============================================
echo.

cd /d "%~dp0"

:: Check if Java is available
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java is not installed or not in PATH.
    echo Please install Java 8+ from https://adoptium.net
    echo.
    pause
    exit /b 1
)

:: Check if JAR exists
if not exist "OFFLOAD-HPC.jar" (
    echo [ERROR] OFFLOAD-HPC.jar not found in current directory.
    echo Make sure this script is in the same folder as OFFLOAD-HPC.jar
    pause
    exit /b 1
)

set NODE_ID=%~1
set PRIORITY=%~2
set TCP_PORT=%~3
set RMI_PORT=%~4

if "%NODE_ID%"=="" (
    echo.
    echo Enter a unique name for this node ^(e.g. pc1, pc2, laptop^):
    set /p NODE_ID="  Node ID: "
)

if "%PRIORITY%"=="" (
    echo Enter priority ^(higher number = more likely to become broker^):
    set /p PRIORITY="  Priority [5]: "
)
if "%PRIORITY%"=="" set PRIORITY=5
if "%TCP_PORT%"=="" set TCP_PORT=9000
if "%RMI_PORT%"=="" set RMI_PORT=1099

echo.
echo Starting node '%NODE_ID%' with priority %PRIORITY%...
echo Make sure you are on the same Wi-Fi/LAN as other nodes.
echo.

java -jar OFFLOAD-HPC.jar %NODE_ID% %PRIORITY% %TCP_PORT% %RMI_PORT%

pause
