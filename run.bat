@echo off
setlocal
cd /d "%~dp0"

set "CP_LIBS=lib\*"

if not exist "out" mkdir out

echo Compiling Java source files from src\...
dir /s /b src\*.java > "%TEMP%\oems_sources.txt"
javac -cp "%CP_LIBS%" -d out @"%TEMP%\oems_sources.txt"
set "COMPILE_STATUS=%ERRORLEVEL%"
del "%TEMP%\oems_sources.txt" 2>nul

if not "%COMPILE_STATUS%"=="0" (
    echo [ERROR] Compilation failed.
    pause
    exit /b 1
)

echo Launching Online Examination Management System...
java -cp "out;%CP_LIBS%" com.onlineexam.main.Main
endlocal
