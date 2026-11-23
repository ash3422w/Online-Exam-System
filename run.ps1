Set-Location -Path $PSScriptRoot

$cpLibs = "lib\*"

if (-not (Test-Path "out")) {
    New-Item -ItemType Directory -Path "out" | Out-Null
}

Write-Host "Compiling Java source files from src\..." -ForegroundColor Cyan
$sources = Get-ChildItem -Path src -Filter *.java -Recurse | Select-Object -ExpandProperty FullName
javac -cp "$cpLibs" -d out $sources

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Compilation failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Launching Online Examination Management System..." -ForegroundColor Green
java -cp "out;$cpLibs" com.onlineexam.main.Main
