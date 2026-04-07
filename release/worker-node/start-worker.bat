@echo off
echo ============================================
echo   OFFLOAD-HPC Worker Node
echo ============================================
echo.

cd /d "%~dp0"

:: --- Configuration ---
:: Worker ID: w1, w2, etc. (required)
:: Broker Host: IP of the Broker machine (required)
:: Broker Port: TCP port of the Broker, default 9000
:: RMI Port: RMI registry port, default 1099 (use 1100 for second worker on same PC)

set WORKER_ID=%~1
set BROKER_HOST=%~2
set BROKER_PORT=%~3
set RMI_PORT=%~4

if "%WORKER_ID%"=="" (
    echo Usage: start-worker.bat ^<workerId^> ^<brokerHost^> [brokerPort] [rmiPort]
    echo.
    echo Examples:
    echo   start-worker.bat w1 192.168.1.3
    echo   start-worker.bat w1 192.168.1.3 9000
    echo   start-worker.bat w1 192.168.1.3 9000 1099
    echo   start-worker.bat w2 192.168.1.3 9000 1100  ^(second worker on same PC^)
    echo.
    echo For local testing:
    echo   start-worker.bat w1 127.0.0.1 9000 1099
    echo   start-worker.bat w2 127.0.0.1 9000 1100
    echo.
    pause
    exit /b 1
)

if "%BROKER_HOST%"=="" (
    echo [ERROR] Broker host is required.
    pause
    exit /b 1
)

if "%BROKER_PORT%"=="" set BROKER_PORT=9000
if "%RMI_PORT%"=="" set RMI_PORT=1099

echo Starting Worker %WORKER_ID%...
echo   Broker: %BROKER_HOST%:%BROKER_PORT%
echo   RMI Port: %RMI_PORT%
echo   Press Ctrl+C to stop.
echo.

java -cp out com.offloadhpc.worker.WorkerMain %WORKER_ID% %BROKER_HOST% %BROKER_PORT% %RMI_PORT%

pause
