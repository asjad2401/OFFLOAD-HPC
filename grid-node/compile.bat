@echo off
echo ============================================
echo   OFFLOAD-HPC Grid Node v2.0 - Compile
echo ============================================
echo.

cd /d "%~dp0"

:: Create output directory
if not exist target\classes mkdir target\classes

echo Compiling Grid Node source files...
javac -encoding UTF-8 -cp "lib\json-20231013.jar" -d target\classes ^
  src\main\java\com\offloadhpc\contract\WorkerService.java ^
  src\main\java\com\offloadhpc\node\*.java ^
  src\main\java\com\offloadhpc\discovery\*.java ^
  src\main\java\com\offloadhpc\broker\model\*.java ^
  src\main\java\com\offloadhpc\broker\registry\*.java ^
  src\main\java\com\offloadhpc\broker\scheduler\*.java ^
  src\main\java\com\offloadhpc\broker\server\*.java ^
  src\main\java\com\offloadhpc\worker\*.java ^
  src\main\java\com\offloadhpc\worker\compute\*.java ^
  src\main\java\com\offloadhpc\worker\rmi\*.java ^
  src\main\java\com\offloadhpc\worker\test\*.java ^
  src\main\java\com\offloadhpc\ui\*.java

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [OK] Grid Node compiled successfully.
    echo     Output: target\classes\
) else (
    echo.
    echo [ERROR] Compilation failed!
    pause
    exit /b 1
)
echo.
pause
