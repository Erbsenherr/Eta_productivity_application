# Builds the release APK with this minute's stamp and signs it into dist/.
#
#   .\tools\build-apk.ps1            -> prints the stamp, e.g. 2026-10-19-1412
#
# The stamp is the APK's versionName, its file name and, through
# publish-release.ps1, the tag of its GitHub release, which the app's
# "App-Update" page compares itself with. Signed with the debug keystore, as
# every APK so far: Android only updates over the same key.
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }

$stamp = Get-Date -Format "yyyy-MM-dd-HHmm"

Push-Location $root
try {
    & .\gradlew.bat assembleRelease "-PetaStamp=$stamp" --quiet
    if ($LASTEXITCODE -ne 0) { throw "assembleRelease failed" }

    $bt = "$env:ANDROID_HOME\build-tools\36.1.0"
    $out = "dist\eta-release-$stamp.apk"
    & "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" `
        --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey `
        --out $out app\build\outputs\apk\release\app-release-unsigned.apk
    if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
} finally {
    Pop-Location
}

Write-Output $stamp
