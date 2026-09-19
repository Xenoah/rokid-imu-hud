#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ ! -f app/build/outputs/apk/debug/app-debug.apk ]; then
    echo 'Build first: tools/build.sh' >&2
    exit 1
fi
# Use ANDROID_SERIAL for multiple attached devices.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.xenoah.rokidhud/dev.xenoah.hud.MainActivity
