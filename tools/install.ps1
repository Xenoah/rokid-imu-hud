param([string]$Serial = '', [string]$Apk = '')
$ErrorActionPreference = 'Stop'
if ($Apk) { $Apk = (Resolve-Path $Apk).Path }
Set-Location (Join-Path $PSScriptRoot '..')
$adbArgs = @()
if ($Serial) { $adbArgs = @('-s', $Serial) }
if (-not $Apk) {
    if (Test-Path 'app/build/outputs/apk/debug/app-debug.apk') {
        $Apk = 'app/build/outputs/apk/debug/app-debug.apk'
    } else {
        $bundled = @(Get-ChildItem -Path 'dist/*.apk' -ErrorAction SilentlyContinue)
        if ($bundled.Count -ne 1) { throw 'Select an APK with -Apk, or build with tools/build.ps1.' }
        $Apk = $bundled[0].FullName
    }
}
& adb @adbArgs install -r $Apk
if ($LASTEXITCODE -ne 0) { throw 'ADB install failed.' }
$launch = & adb @adbArgs shell am start -W -n dev.xenoah.rokidhud/dev.xenoah.hud.MainActivity
$launchExit = $LASTEXITCODE
$launch | Write-Output
if ($launchExit -ne 0 -or ($launch -join "`n") -notmatch 'Status: ok') {
    throw 'ADB launch failed. Run tools/debug-device.py to capture startup logs.'
}
