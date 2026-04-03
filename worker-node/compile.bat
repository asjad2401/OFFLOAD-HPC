@echo off
echo ============================================
echo   OFFLOAD-HPC Worker Node - Compile
echo ============================================
echo.

cd /d "%~dp0"

:: Create output directory
if not exist out mkdir out

echo Compiling Worker source files...
javac -d out ^
  src\com\offloadhpc\contract\WorkerService.java ^
  src\com\offloadhpc\worker\compute\*.java ^
  src\com\offloadhpc\worker\rmi\WorkerServiceImpl.java ^
  src\com\offloadhpc\worker\network\BrokerRegistrar.java ^
  src\com\offloadhpc\worker\WorkerMain.java ^
  src\com\offloadhpc\worker\test\WorkerTest.java

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [OK] Worker compiled successfully.
    echo     Output: out\
) else (
    echo.
    echo [ERROR] Compilation failed!
    pause
    exit /b 1
)
echo.
pause
