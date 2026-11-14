# (see PLAN.md)
# (see PLAN.md)
$ErrorActionPreference = 'Stop'
$root = Join-Path $env:LOCALAPPDATA 'EbisToolchain'
$env:JAVA_HOME = (Get-ChildItem (Join-Path $root 'jdk') -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = Join-Path $root 'android-sdk'
$gradle = Join-Path (Get-ChildItem (Join-Path $root 'gradle') -Directory | Select-Object -First 1).FullName 'bin\gradle.bat'

Set-Content -Path (Join-Path $PSScriptRoot 'local.properties') -Value ("sdk.dir=" + ($env:ANDROID_HOME -replace '\\', '/')) -Encoding ascii

Push-Location $PSScriptRoot
try {
    & $gradle assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Build failed (exit code $LASTEXITCODE)" }
} finally { Pop-Location }

$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
Copy-Item $apk (Join-Path $PSScriptRoot 'ebis-ono-konem.apk') -Force
Write-Host "APK: $(Join-Path $PSScriptRoot 'ebis-ono-konem.apk')"
