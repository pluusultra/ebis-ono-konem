﻿# ПК-версия под Windows. Нужен тулчейн из setup-toolchain.ps1 (JDK 17).
#   .\build-desktop.ps1                  — папка с EbisOnoKonem.exe (без установки) + переносной jar
#   .\build-desktop.ps1 -Installer       — ещё и установщик EbisOnoKonem-<версия>-windows-x64-setup.exe
#                                          (нужен Inno Setup 6: winget install JRSoftware.InnoSetup)
#   .\build-desktop.ps1 -Version 2.1.0   — своя версия вместо автоматической
param([switch]$Installer, [string]$Version)
$ErrorActionPreference = 'Stop'
$root = Join-Path $env:LOCALAPPDATA 'EbisToolchain'
$env:JAVA_HOME = (Get-ChildItem (Join-Path $root 'jdk') -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = Join-Path $root 'android-sdk'

$tasks = @(':desktop:createDistributable', ':desktop:packageUberJarForCurrentOS')
if ($Version) { $tasks += "-PappVersion=$Version" }

Push-Location $PSScriptRoot
try {
    & .\gradlew.bat @tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Build failed (exit code $LASTEXITCODE)" }
} finally { Pop-Location }

$out = Join-Path $PSScriptRoot 'dist\desktop'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $out | Out-Null
$bin = Join-Path $PSScriptRoot 'desktop\build\compose'
$image = Join-Path $bin 'binaries\main\app\EbisOnoKonem'
Copy-Item $image (Join-Path $out 'EbisOnoKonem') -Recurse
# Свежий по времени: старые сборки с другой версией в имени остаются в той же папке.
# Jar содержит отрисовку и для Windows, и для Linux: на любой из них запускается через `java -jar` (Java 17+).
Copy-Item (Get-ChildItem (Join-Path $bin 'jars') -Filter '*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName (Join-Path $out 'ebis-ono-konem.jar')

if ($Installer) {
    $iscc = @("${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe", "$env:ProgramFiles\Inno Setup 6\ISCC.exe", "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe") |
        Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $iscc) { throw 'Нет Inno Setup 6: winget install JRSoftware.InnoSetup' }
    # Версия — та же, что jpackage записал в собранное приложение.
    $cfg = Get-Content (Join-Path $image 'app\EbisOnoKonem.cfg') -Raw
    $ver = if ($cfg -match 'app-version=([0-9.]+)') { $Matches[1] } else { '0.0.0' }
    & $iscc /Q "/DAppVersion=$ver" "/DOutputDir=$out" (Join-Path $PSScriptRoot 'packaging\windows\ebis.iss')
    if ($LASTEXITCODE -ne 0) { throw "Inno Setup failed (exit code $LASTEXITCODE)" }
}
Write-Host "Готово: $out"
Write-Host "Запуск: $(Join-Path $out 'EbisOnoKonem\EbisOnoKonem.exe')"
