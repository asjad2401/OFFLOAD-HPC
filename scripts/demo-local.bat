@echo off
echo ============================================
echo   OFFLOAD-HPC Local Demo
echo   Starts: Broker + 2 Workers
echo ============================================
echo.

cd /d "%~dp0"

:: ---- Compile everything first ----
echo [1/3] Compiling Broker...
cd broker-node
javac -cp "lib\json-20231013.jar" -d target\classes ^
  src\main\java\com\offloadhpc\contract\WorkerService.java ^
  src\main\java\com\offloadhpc\broker\model\*.java ^
  src\main\java\com\offloadhpc\broker\registry\*.java ^
  src\main\java\com\offloadhpc\broker\scheduler\*.java ^
  src\main\java\com\offloadhpc\broker\server\*.java
if %ERRORLEVEL% NEQ 0 ( echo [ERROR] Broker compile failed! & pause & exit /b 1 )
echo       [OK] Broker compiled.
cd ..

echo [2/3] Compiling Worker...
cd worker-node
if not exist out mkdir out
javac -d out ^
  src\com\offloadhpc\contract\WorkerService.java ^
  src\com\offloadhpc\worker\compute\*.java ^
  src\com\offloadhpc\worker\rmi\WorkerServiceImpl.java ^
  src\com\offloadhpc\worker\network\BrokerRegistrar.java ^
  src\com\offloadhpc\worker\WorkerMain.java ^
  src\com\offloadhpc\worker\test\WorkerTest.java
if %ERRORLEVEL% NEQ 0 ( echo [ERROR] Worker compile failed! & pause & exit /b 1 )
echo       [OK] Worker compiled.
cd ..

echo [3/3] Compiling Integration Test...
cd broker-node
javac IntegrationTest.java
if %ERRORLEVEL% NEQ 0 ( echo [ERROR] Test compile failed! & pause & exit /b 1 )
cd ..

echo.
echo All components compiled successfully!
echo.

:: ---- Start services ----
echo Starting Broker (port 9000)...
start "OFFLOAD-HPC Broker" cmd /k "cd /d %~dp0broker-node && java -cp target\classes;lib\json-20231013.jar com.offloadhpc.broker.server.BrokerServer 9000"
timeout /t 2 /nobreak > nul

echo Starting Worker w1 (RMI 1099)...
start "OFFLOAD-HPC Worker w1" cmd /k "cd /d %~dp0worker-node && java -cp out com.offloadhpc.worker.WorkerMain w1 127.0.0.1 9000 1099"
timeout /t 2 /nobreak > nul

echo Starting Worker w2 (RMI 1100)...
start "OFFLOAD-HPC Worker w2" cmd /k "cd /d %~dp0worker-node && java -cp out com.offloadhpc.worker.WorkerMain w2 127.0.0.1 9000 1100"
timeout /t 3 /nobreak > nul

echo.
echo ============================================
echo   All services started!
echo   Broker: port 9000
echo   Worker w1: RMI port 1099
echo   Worker w2: RMI port 1100
echo ============================================
echo.
echo Press any key to run integration tests...
pause > nul

cd broker-node
java IntegrationTest 127.0.0.1 9000

echo.
echo Close the Broker and Worker windows to stop the demo.
pause
