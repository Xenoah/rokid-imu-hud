$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
& .\gradlew.bat :core:verify :app:assembleDebug :app:lintDebug --no-daemon
if ($LASTEXITCODE -ne 0) { throw 'Build failed; see the first Gradle error above.' }
Write-Host 'APK: app/build/outputs/apk/debug/app-debug.apk'
