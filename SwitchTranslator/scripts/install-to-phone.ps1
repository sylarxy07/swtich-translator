# APK yukle: JDK 17 + SSL (PKIX) icin ortam ayarlanir.
# Calistir:  cd SwitchTranslator   .\scripts\install-to-phone.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

# --- JDK 17 (Gradle 8 / AGP 8 sart) ---
function Find-Jdk17 {
    $candidates = @(
        $env:JAVA_HOME,
        "$env:ProgramFiles\Android\Android Studio\jbr",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr",
        "$env:ProgramFiles\Eclipse Adoptium\jdk-17.0.13.11-hotspot"
    )
    foreach ($c in $candidates) {
        if ([string]::IsNullOrWhiteSpace($c)) { continue }
        $java = Join-Path $c "bin\java.exe"
        if (Test-Path -LiteralPath $java) {
            $ver = & $java -version 2>&1 | Out-String
            if ($ver -match '"17\.|"21\.') { return $c }
        }
    }
    Get-ChildItem "$env:ProgramFiles\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "jdk-17*" } | ForEach-Object {
        $java = Join-Path $_.FullName "bin\java.exe"
        if (Test-Path $java) { return $_.FullName }
    }
    return $null
}

$jdk = Find-Jdk17
if ($jdk) {
    $env:JAVA_HOME = $jdk
    $env:Path = "$(Join-Path $jdk 'bin');$env:Path"
    Write-Host "JAVA_HOME: $jdk"
} else {
    Write-Warning "JDK 17 bulunamadi. Android Studio kur veya Temurin 17 ZIP ac; gradle.properties -> org.gradle.java.home"
}

# --- PKIX / kurumsal ag: Windows sertifika deposu (Java 11+) ---
$ssl = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
$env:JAVA_TOOL_OPTIONS = "$ssl $env:JAVA_TOOL_OPTIONS".Trim()
$env:GRADLE_OPTS = "$ssl $env:GRADLE_OPTS".Trim()

$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
Write-Host "Derleniyor: assembleDebug"
& .\gradlew.bat assembleDebug --no-daemon
if (-not (Test-Path $apk)) {
    Write-Host ""
    Write-Host "PKIX / SSL: Kurumsal agda Maven indirilemiyorsa IT kok sertifika veya ev agi gerekir." -ForegroundColor Yellow
    Write-Host "JDK 17 sart (Android Studio jbr veya Temurin 17)." -ForegroundColor Yellow
    Write-Error "APK yok: $apk"
}

$adb = $null
$lp = Join-Path $root "local.properties"
if (Test-Path $lp) {
    foreach ($line in Get-Content $lp) {
        if ($line -match '^\s*sdk\.dir\s*=\s*(.+)\s*$') {
            $sdkDir = $Matches[1].Trim() -replace '/', '\'
            $cand = Join-Path $sdkDir "platform-tools\adb.exe"
            if (Test-Path $cand) { $adb = $cand; break }
        }
    }
}
if (-not $adb -and $env:ANDROID_HOME) {
    $cand = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
    if (Test-Path $cand) { $adb = $cand }
}
if (-not $adb) {
    Write-Warning "adb bulunamadi. Android SDK platform-tools gerekli. APK hazir: $apk"
    exit 0
}

Write-Host "Cihazlar:"; & $adb devices
Write-Host "Yukleniyor..."
& $adb install -r $apk
if ($LASTEXITCODE -eq 0) { Write-Host "Tamam: Uygulama yuklendi." }
