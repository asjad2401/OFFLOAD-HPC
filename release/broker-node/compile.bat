@echo off
echo ============================================
echo   OFFLOAD-HPC Broker Node - Compile
echo ============================================
echo.

cd /d "%~dp0"

echo Compiling Broker source files...
javac -cp "lib\json-20231013.jar" -d target\classes ^
  src\main\java\com\offloadhpc\contract\WorkerService.java ^
  src\main\java\com\offloadhpc\broker\model\*.java ^
  src\main\java\com\offloadhpc\broker\registry\*.java ^
  src\main\java\com\offloadhpc\broker\scheduler\*.java ^
  src\main\java\com\offloadhpc\broker\server\*.java

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [OK] Broker compiled successfully.
    echo     Output: target\classes\
) else (
    echo.
    echo [ERROR] Compilation failed!
    pause
    exit /b 1
)
echo.
pause
