Set-Location -Path $PSScriptRoot

$cpLibs = "lib\mysql-connector-j.jar;lib\flatlaf.jar;lib\openpdf-1.3.40.jar;lib\core-3.5.3.jar;lib\webcam-capture-0.3.12.jar;lib\bridj-0.7.0.jar;lib\slf4j-api-1.7.36.jar;lib\slf4j-nop-1.7.36.jar"

if (-not (Test-Path "out")) {
    New-Item -ItemType Directory -Path "out" | Out-Null
}

Write-Host "Compiling Java source files..." -ForegroundColor Cyan
javac -cp "$cpLibs" -d out *.java

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Compilation failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Launching Online Examination Management System..." -ForegroundColor Green
java -cp "out;$cpLibs" com.onlineexam.main.Main
