#!/usr/bin/env bash
# 打 release APK 并复制到 dist/，再 gradle clean 清理构建产物（便于 git）。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -x "./gradlew" ]]; then
  GRADLE=(./gradlew)
else
  GRADLE=(gradle)
fi

"${GRADLE[@]}" assembleRelease --no-daemon -q

OUT="$ROOT/app/build/outputs/apk/release/iTap.apk"
mkdir -p "$ROOT/dist"
cp -f "$OUT" "$ROOT/dist/iTap.apk"
echo "APK -> $ROOT/dist/iTap.apk ($(wc -c < "$ROOT/dist/iTap.apk" | tr -d ' ') bytes)"

"${GRADLE[@]}" clean --no-daemon -q
echo "gradle clean 完成。"
