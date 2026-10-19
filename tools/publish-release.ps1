# Publishes dist/eta-release-<stamp>.apk as a GitHub release, its patch notes
# as the text. Run after the session's commit is pushed: the tag lands on main.
#
#   .\tools\publish-release.ps1 2026-10-19-1412
#
# The tag is the stamp, which is what the app's "App-Update" page compares with
# its own versionName. Needs the GitHub CLI, signed in (gh auth login).
param([Parameter(Mandatory)][string]$Stamp)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

$gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
if (-not $gh) { $gh = "C:\Program Files\GitHub CLI\gh.exe" }

$apk = Join-Path $root "dist\eta-release-$Stamp.apk"
$notes = Join-Path $root "patchnotes\$Stamp.md"
if (-not (Test-Path $apk)) { throw "No APK at $apk" }

Push-Location $root
try {
    if (Test-Path $notes) {
        & $gh release create $Stamp $apk --target main --title "Eta $Stamp" --notes-file $notes
    } else {
        & $gh release create $Stamp $apk --target main --title "Eta $Stamp" --notes ""
    }
    if ($LASTEXITCODE -ne 0) { throw "gh release create failed" }
} finally {
    Pop-Location
}
