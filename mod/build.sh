#!/bin/bash
# Builds and tests the mod core. Usage: build.sh [test|dex|all]
set -euo pipefail
export JAVA_TOOL_OPTIONS=
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${VKMOD_WORK:-/home/user/work}"
SDK="${ANDROID_HOME:-$WORK/sdk}"
AJ="$SDK/platforms/android-35/android.jar"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
OUT="${VKMOD_OUT:-$WORK/build}"
LIBS="$WORK/libs"
mkdir -p "$OUT"

compile() {
  rm -rf "$OUT/classes"; mkdir -p "$OUT/classes"
  javac -nowarn -source 8 -target 8 -cp "$AJ" -d "$OUT/classes" $(find "$HERE/core/src" -name '*.java') 2>&1 | grep -v '^Note\|warning: \[options\]\|^Picked' || true
  test -f "$OUT/classes/com/vkmod/Hooks.class"
}

run_tests() {
  rm -rf "$OUT/test"; mkdir -p "$OUT/test"
  local cp="$OUT/classes:$LIBS/junit-4.13.2.jar:$LIBS/hamcrest-core-1.3.jar:$LIBS/json-20240303.jar"
  # org.json comes from the JVM jar for tests (android.jar stubs would throw); android.jar only for linking
  javac -nowarn -source 8 -target 8 -cp "$cp:$AJ" -d "$OUT/test" $(find "$HERE/core/test" -name "*.java") 2>&1 | grep -v '^Note\|warning: \[options\]\|^Picked' || true
  java -cp "$OUT/test:$cp:$AJ" org.junit.runner.JUnitCore com.vkmod.NetLogicTest com.vkmod.ThemeLogicTest com.vkmod.MediaExtractTest com.vkmod.NetHookTest
}

make_dex() {
  rm -rf "$OUT/dex"; mkdir -p "$OUT/dex"
  "$BT/d8" --release --min-api 28 --lib "$AJ" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
  cp "$OUT/dex/classes.dex" "$OUT/core.dex"
  echo "core.dex: $(stat -c %s "$OUT/core.dex") bytes"
}

case "${1:-all}" in
  test) compile; run_tests ;;
  dex) compile; make_dex ;;
  all) compile; run_tests; make_dex ;;
esac
