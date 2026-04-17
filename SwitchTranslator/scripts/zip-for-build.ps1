# SwitchTranslator'u baska PC'de derlemek icin zip uretir.
# Calistir:  powershell -ExecutionPolicy Bypass -File .\scripts\zip-for-build.ps1
# Cikti:    Masaustunde SwitchTranslator-for-build-YYYYMMDD-HHMM.zip

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$zipName = "SwitchTranslator-for-build-$stamp.zip"
$desktop = [Environment]::GetFolderPath("Desktop")
$destZip = Join-Path $desktop $zipName
$temp = Join-Path $env:TEMP "st-build-zip-$stamp"

if (-not (Test-Path (Join-Path $ProjectRoot "gradlew.bat"))) {
    Write-Error "gradlew.bat bulunamadi. Script SwitchTranslator klasorunun icindeki scripts/ altinda olmali."
}

if (Test-Path $temp) { Remove-Item $temp -Recurse -Force }
New-Item -ItemType Directory -Path $temp -Force | Out-Null

# Gereksiz / tekrar uretilebilir klasorleri atla (zip kucuk ve temiz olsun)
$xd = @(
    ".git", ".gradle", ".idea", "captures",
    "build", "app\build"
)
$xf = @("*.apk", "*.aab", "*.iml", ".DS_Store")

$args = @(
    $ProjectRoot, $temp, "/E",
    "/NFL", "/NDL", "/NJH", "/NJS", "/nc", "/ns", "/np"
)
foreach ($d in $xd) { $args += "/XD"; $args += $d }
foreach ($f in $xf) { $args += "/XF"; $args += $f }
$args += "/XF"; $args += "local.properties"

& robocopy @args
if ($LASTEXITCODE -ge 8) { Write-Error "robocopy hata kodu: $LASTEXITCODE" }

Compress-Archive -Path (Join-Path $temp "*") -DestinationPath $destZip -Force
Remove-Item $temp -Recurse -Force

Write-Host ""
Write-Host "Tamam: $destZip"
Write-Host "Bu zip'i USB ile tasiyip baska PC'de ac; Android Studio ile Open veya gradlew assembleDebug kullan."
Write-Host ""
