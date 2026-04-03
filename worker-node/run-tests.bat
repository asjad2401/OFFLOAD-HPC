@echo off
echo ============================================
echo   OFFLOAD-HPC Worker Node - Unit Tests
echo ============================================
echo.

cd /d "%~dp0"

echo Running worker unit tests...
echo.

java -cp out com.offloadhpc.worker.test.WorkerTest

echo.
pause
