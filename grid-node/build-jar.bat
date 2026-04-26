@echo off
echo ============================================
echo   OFFLOAD-HPC Grid Node - Build JAR
echo ============================================
echo.

cd /d "%~dp0"

:: Step 1: Compile (in case not done yet)
echo [1/4] Compiling source files...
if not exist "target\classes" mkdir "target\classes"
javac -encoding UTF-8 -cp "lib\json-20231013.jar" -d target\classes src\main\java\com\offloadhpc\contract\*.java src\main\java\com\offloadhpc\discovery\*.java src\main\java\com\offloadhpc\node\*.java src\main\java\com\offloadhpc\broker\model\*.java src\main\java\com\offloadhpc\broker\registry\*.java src\main\java\com\offloadhpc\broker\scheduler\*.java src\main\java\com\offloadhpc\broker\server\*.java src\main\java\com\offloadhpc\worker\*.java src\main\java\com\offloadhpc\worker\rmi\*.java src\main\java\com\offloadhpc\worker\compute\*.java src\main\java\com\offloadhpc\ui\*.java
if errorlevel 1 (
    echo [FAIL] Compilation failed.
    pause
    exit /b 1
)
echo [OK] Compiled.

:: Step 2: Extract the JSON library into the classes folder (fat jar)
echo [2/4] Bundling dependencies...
cd target\classes
jar xf ..\..\lib\json-20231013.jar
:: Remove the META-INF from the library (we'll make our own)
if exist META-INF rmdir /s /q META-INF
cd ..\..

:: Step 3: Create manifest
echo [3/4] Creating manifest...
if not exist "target" mkdir "target"
echo Main-Class: com.offloadhpc.node.GridNode> target\MANIFEST.MF

:: Step 4: Build the JAR
echo [4/4] Building JAR...
jar cfm target\OFFLOAD-HPC.jar target\MANIFEST.MF -C target\classes .
if errorlevel 1 (
    echo [FAIL] JAR creation failed.
    pause
    exit /b 1
)

echo.
echo ============================================
echo   [OK] Built: target\OFFLOAD-HPC.jar
echo ============================================
echo.
echo To run with GUI:
echo   java -jar OFFLOAD-HPC.jar n1 10
echo.
echo To run headless:
echo   java -jar OFFLOAD-HPC.jar n1 10 9000 1099 --headless
echo.
echo Or use the launcher: start-grid-node.bat
echo.
pause
