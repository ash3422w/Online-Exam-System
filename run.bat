@echo off
setlocal
cd /d "%~dp0"

set "CP_LIBS=lib\mysql-connector-j.jar;lib\flatlaf.jar"

rem Task 11: OpenPDF and ZXing core libraries
if exist "lib\openpdf-1.3.40.jar" (
    set "CP_LIBS=%CP_LIBS%;lib\openpdf-1.3.40.jar"
) else if exist "lib\openpdf.jar" (
    set "CP_LIBS=%CP_LIBS%;lib\openpdf.jar"
)

if exist "lib\core-3.5.3.jar" (
    set "CP_LIBS=%CP_LIBS%;lib\core-3.5.3.jar"
) else if exist "lib\core.jar" (
    set "CP_LIBS=%CP_LIBS%;lib\core.jar"
)

rem Task 12: Webcam Capture and dependencies
if exist "lib\webcam-capture-0.3.12.jar" (
    set "CP_LIBS=%CP_LIBS%;lib\webcam-capture-0.3.12.jar"
) else if exist "lib\webcam-capture.jar" (
    set "CP_LIBS=%CP_LIBS%;lib\webcam-capture.jar"
)

if exist "lib\bridj-0.7.0.jar" (
    set "CP_LIBS=%CP_LIBS%;lib\bridj-0.7.0.jar"
) else if exist "lib\bridj.jar" (
    set "CP_LIBS=%CP_LIBS%;lib\bridj.jar"
)

if exist "lib\slf4j-api-1.7.36.jar" (
    set "CP_LIBS=%CP_LIBS%;lib\slf4j-api-1.7.36.jar"
) else if exist "lib\slf4j-api.jar" (
    set "CP_LIBS=%CP_LIBS%;lib\slf4j-api.jar"
)

if exist "lib\slf4j-nop-1.7.36.jar" (
    set "CP_LIBS=%CP_LIBS%;lib\slf4j-nop-1.7.36.jar"
) else if exist "lib\slf4j-nop.jar" (
    set "CP_LIBS=%CP_LIBS%;lib\slf4j-nop.jar"
)
if not exist "lib\mysql-connector-j.jar" (
    echo [ERROR] MySQL Connector JAR not found at "lib\mysql-connector-j.jar".
    pause
    exit /b 1
)
if not exist "lib\flatlaf.jar" (
    echo [ERROR] FlatLaf JAR not found at "lib\flatlaf.jar".
    pause
    exit /b 1
)

if not exist "out" mkdir out

echo Compiling Java source files...
javac -cp "%CP_LIBS%" -d out *.java
if errorlevel 1 (
    echo [ERROR] Compilation failed.
    pause
    exit /b 1
)

echo Launching Online Examination Management System...
java -cp "out;%CP_LIBS%" com.onlineexam.main.Main
endlocal
