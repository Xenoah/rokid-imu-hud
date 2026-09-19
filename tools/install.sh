#!/usr/bin/env sh
set -eu
apk=${1:-}
if [ -n "$apk" ]; then
    case "$apk" in /*) ;; *) apk="$PWD/$apk" ;; esac
fi
cd "$(dirname "$0")/.."
if [ -z "$apk" ]; then
    if [ -f app/build/outputs/apk/debug/app-debug.apk ]; then
        apk=app/build/outputs/apk/debug/app-debug.apk
    else
        set -- dist/*.apk
        if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
            echo 'Specify an APK path, or build with tools/build.sh.' >&2
            exit 1
        fi
        apk=$1
    fi
fi
# Use ANDROID_SERIAL for multiple attached devices.
adb install -r "$apk"
launch_output=$(adb shell am start -W -n dev.xenoah.rokidhud/dev.xenoah.hud.MainActivity)
printf '%s\n' "$launch_output"
case "$launch_output" in
    *'Status: ok'*) ;;
    *) echo 'Launch failed. Run tools/debug-device.py to capture startup logs.' >&2; exit 1 ;;
esac
