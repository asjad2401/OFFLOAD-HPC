@echo off
echo ============================================
echo   OFFLOAD-HPC Grid Node v2.0
echo ============================================
echo.

cd /d "%~dp0"

:: --- Configuration ---
:: nodeId   - unique node identifier (required)
:: priority - election priority, higher wins (default: auto)
:: tcpPort  - TCP port for broker role (default: 9000)
:: rmiPort  - RMI port for worker role (default: 1099)

set NODE_ID=%~1
set PRIORITY=%~2
set TCP_PORT=%~3
set RMI_PORT=%~4

if "%NODE_ID%"=="" (
    echo Usage: start-node.bat ^<nodeId^> [priority] [tcpPort] [rmiPort]
    echo.
    echo Examples:
    echo   start-node.bat n1 10
    echo   start-node.bat n2 5
    echo   start-node.bat n1 10 9000 1099
    echo   start-node.bat n2 5 9000 1100  ^(second node on same PC^)
    echo.
    echo For local testing ^(3 nodes on same PC^):
    echo   start-node.bat n1 10 9000 1099
    echo   start-node.bat n2 5 9001 1100
    echo   start-node.bat n3 3 9002 1101
    echo.
    pause
    exit /b 1
)

:: Set defaults
if "%PRIORITY%"=="" set PRIORITY=5
if "%TCP_PORT%"=="" set TCP_PORT=9000
if "%RMI_PORT%"=="" set RMI_PORT=1099

echo Starting GridNode %NODE_ID%...
echo   Priority: %PRIORITY%
echo   TCP Port: %TCP_PORT% (used if elected as broker)
echo   RMI Port: %RMI_PORT%
echo   Press Ctrl+C to stop.
echo.

java -cp "target\classes;lib\json-20231013.jar" com.offloadhpc.node.GridNode %NODE_ID% %PRIORITY% %TCP_PORT% %RMI_PORT%

pause
