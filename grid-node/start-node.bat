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
:: --headless - run without GUI (CLI only)

set NODE_ID=%~1
set PRIORITY=%~2
set TCP_PORT=%~3
set RMI_PORT=%~4
set HEADLESS=%~5

if "%NODE_ID%"=="" (
    echo Usage: start-node.bat ^<nodeId^> [priority] [tcpPort] [rmiPort] [--headless]
    echo.
    echo Examples:
    echo   start-node.bat n1 10
    echo   start-node.bat n2 5
    echo   start-node.bat n1 10 9000 1099
    echo   start-node.bat n2 5 9000 1100  ^(second node on same PC^)
    echo   start-node.bat n1 10 9000 1099 --headless
    echo.
    echo For LAN deployment ^(one node per machine^):
    echo   Machine A: start-node.bat broker 10
    echo   Machine B: start-node.bat worker1 5
    echo   Machine C: start-node.bat worker2 3
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
if "%HEADLESS%"=="--headless" echo   Mode: Headless (no GUI)
echo   Press Ctrl+C to stop.
echo.

java -cp "target\classes;lib\json-20231013.jar" com.offloadhpc.node.GridNode %NODE_ID% %PRIORITY% %TCP_PORT% %RMI_PORT% %HEADLESS%

pause
