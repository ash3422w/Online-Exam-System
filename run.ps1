Set-Location -Path $PSScriptRoot

# Ensure MySQL server is running on port 3306
$port3306 = Get-NetTCPConnection -LocalPort 3306 -ErrorAction SilentlyContinue
if (-not $port3306) {
    Write-Host "MySQL 8.0 server is not running. Starting local MySQL daemon..." -ForegroundColor Yellow
    $mysqld = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe"
    $dataDir = Join-Path $PSScriptRoot "mysql_data"
    if (Test-Path $mysqld) {
        Start-Process -FilePath $mysqld -ArgumentList "--datadir=`"$dataDir`" --port=3306" -WindowStyle Hidden
        Start-Sleep -Seconds 2
        Write-Host "MySQL 8.0 server started." -ForegroundColor Green
    } else {
        Write-Host "[WARNING] mysqld.exe not found at standard path. Please ensure MySQL is running on port 3306." -ForegroundColor Yellow
    }
}

$cpLibs = "lib\*"

if (-not (Test-Path "out")) {
    New-Item -ItemType Directory -Path "out" | Out-Null
}

Write-Host "Compiling Java source files from java files\..." -ForegroundColor Cyan
$sources = Get-ChildItem -Path "java files" -Filter *.java -Recurse | Select-Object -ExpandProperty FullName
javac -cp "$cpLibs" -d out $sources

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Compilation failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Launching Online Examination Management System..." -ForegroundColor Green
java -cp "out;$cpLibs" com.onlineexam.main.Main
