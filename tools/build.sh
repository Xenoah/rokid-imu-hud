#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
./gradlew :core:verify :app:assembleDebug :app:lintDebug --no-daemon
