@echo off
echo ============================================
echo   OFFLOAD-HPC Broker Node
echo ============================================
echo.

cd /d "%~dp0"

:: Default port
set PORT=9000
if not "%~1"=="" set PORT=%~1

echo Starting Broker on port %PORT%...
echo Press Ctrl+C to stop.
echo.

java -cp "target\classes;lib\json-20231013.jar" com.offloadhpc.broker.server.BrokerServer %PORT%

pause
