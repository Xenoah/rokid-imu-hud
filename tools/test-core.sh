#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p core/build/manual artifacts
find core/src/main/java core/src/test/java -name '*.java' > core/build/manual/sources.txt
java com.sun.tools.javac.Main --release 17 -Xlint:all -d core/build/manual @core/build/manual/sources.txt
java -ea -cp core/build/manual dev.xenoah.hud.core.CoreTests
