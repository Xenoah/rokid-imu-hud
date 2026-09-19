param([string]$Serial = '')
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$adbArgs = @()
if ($Serial) { $adbArgs = @('-s', $Serial) }
if (-not (Test-Path 'app/build/outputs/apk/debug/app-debug.apk')) { throw 'Build the APK first: tools/build.ps1' }
& adb @adbArgs install -r app/build/outputs/apk/debug/app-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'ADB install failed.' }
& adb @adbArgs shell am start -n dev.xenoah.rokidhud/dev.xenoah.hud.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'ADB launch failed.' }
