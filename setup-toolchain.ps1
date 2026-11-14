# (see PLAN.md)
# JDK 17 (Temurin), Android command-line tools + platform 35 + build-tools 35, Gradle 8.11.1.
# (see PLAN.md)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root = Join-Path $env:LOCALAPPDATA 'EbisToolchain'
$dl = Join-Path $root 'downloads'
New-Item -ItemType Directory -Force $root, $dl | Out-Null

function Get-Zip($url, $name, $dest) {
    $zip = Join-Path $dl $name
    if (-not (Test-Path $zip)) { Write-Host "Downloading $name..."; Invoke-WebRequest $url -OutFile $zip }
    if (-not (Test-Path $dest)) { Write-Host "Extracting $name..."; Expand-Archive $zip -DestinationPath $dest -Force }
}

# JDK 17
$jdkDir = Join-Path $root 'jdk'
Get-Zip 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' 'jdk17.zip' $jdkDir
$javaHome = (Get-ChildItem $jdkDir -Directory | Select-Object -First 1).FullName

# Gradle
$gradleDir = Join-Path $root 'gradle'
Get-Zip 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' 'gradle-8.11.1-bin.zip' $gradleDir

# Android SDK
$sdk = Join-Path $root 'android-sdk'
$cmdline = Join-Path $sdk 'cmdline-tools\latest'
if (-not (Test-Path $cmdline)) {
    $tmp = Join-Path $root 'cmdline-tmp'
    Get-Zip 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' 'cmdline-tools.zip' $tmp
    New-Item -ItemType Directory -Force (Split-Path $cmdline) | Out-Null
    Move-Item (Join-Path $tmp 'cmdline-tools') $cmdline
    Remove-Item $tmp -Recurse -Force
}
$env:JAVA_HOME = $javaHome
$sdkmanager = Join-Path $cmdline 'bin\sdkmanager.bat'
Write-Host 'Accepting Android SDK licenses...'
(1..20 | ForEach-Object { 'y' }) | & $sdkmanager --sdk_root=$sdk --licenses | Out-Null
Write-Host 'Installing platform 35 and build-tools 35...'
& $sdkmanager --sdk_root=$sdk 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'

# Fonts
$fontDir = Join-Path $PSScriptRoot 'app\src\main\res\font'
New-Item -ItemType Directory -Force $fontDir | Out-Null
$fonts = @{
    'unbounded.ttf' = 'https://github.com/google/fonts/raw/main/ofl/unbounded/Unbounded%5Bwght%5D.ttf'
    'onest.ttf'     = 'https://github.com/google/fonts/raw/main/ofl/onest/Onest%5Bwght%5D.ttf'
    'jbmono.ttf'    = 'https://github.com/google/fonts/raw/main/ofl/jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf'
}
foreach ($f in $fonts.Keys) {
    $out = Join-Path $fontDir $f
    if (-not (Test-Path $out)) { Write-Host "Downloading font $f..."; Invoke-WebRequest $fonts[$f] -OutFile $out }
}

Write-Host "Done. Toolchain: $root"
