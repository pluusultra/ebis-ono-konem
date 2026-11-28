# (see PLAN.md)
# (see PLAN.md)
param([string]$Version)

$ErrorActionPreference = 'Stop'
$root = Join-Path $env:LOCALAPPDATA 'EbisToolchain'
$env:JAVA_HOME = (Get-ChildItem (Join-Path $root 'jdk') -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = Join-Path $root 'android-sdk'
$gradle = Join-Path (Get-ChildItem (Join-Path $root 'gradle') -Directory | Select-Object -First 1).FullName 'bin\gradle.bat'

Set-Content -Path (Join-Path $PSScriptRoot 'local.properties') -Value ("sdk.dir=" + ($env:ANDROID_HOME -replace '\\', '/')) -Encoding ascii

Push-Location $PSScriptRoot
try {
    $gradleArgs = @(':app:assembleRelease', '--console=plain')
    if ($Version) { $gradleArgs += "-PappVersion=$Version" }
    & $gradle @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Build failed (exit code $LASTEXITCODE)" }
} finally { Pop-Location }

$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
$outDir = Join-Path $PSScriptRoot 'dist\rustore'
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
$out = Join-Path $outDir 'nu-ego-konem.apk'
Copy-Item -LiteralPath $apk -Destination $out -Force
Write-Host "APK: $out"
