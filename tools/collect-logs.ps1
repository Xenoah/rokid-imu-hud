param([string]$Serial = '', [string]$Destination = 'device-logs')
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
New-Item -ItemType Directory -Force $Destination | Out-Null
$adbArgs = @()
if ($Serial) { $adbArgs = @('-s', $Serial) }
& adb @adbArgs shell getprop ro.build.fingerprint | Out-File -Encoding utf8 (Join-Path $Destination 'firmware.txt')
& adb @adbArgs shell wm size | Out-File -Encoding utf8 (Join-Path $Destination 'display.txt')
& adb @adbArgs shell dumpsys sensorservice | Out-File -Encoding utf8 (Join-Path $Destination 'sensors.txt')
& adb @adbArgs logcat -d -v threadtime RokidHUD:I '*:S' | Out-File -Encoding utf8 (Join-Path $Destination 'hud-logcat.txt')
& adb @adbArgs shell run-as dev.xenoah.rokidhud cat files/hud-debug.csv | Out-File -Encoding utf8 (Join-Path $Destination 'hud-debug.csv')
if ($LASTEXITCODE -ne 0) { Write-Warning 'CSV unavailable: launch the debug APK with --ez debug true and collect while installed.' }
Write-Host "Saved diagnostics in $Destination"
