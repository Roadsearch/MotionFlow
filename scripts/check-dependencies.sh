#!/usr/bin/env bash
set -euo pipefail
build="$(cat app/build.gradle.kts)"
versions=$(printf '%s\n' "$build" | grep -oE 'androidx\.media3:[^:]+:[0-9]+\.[0-9]+\.[0-9]+' | sed -E 's/.*:([0-9]+\.[0-9]+\.[0-9]+)$/\1/' | sort -u)
count=$(printf '%s\n' "$versions" | sed '/^$/d' | wc -l | tr -d ' ')
[[ "$count" == "1" ]] || { echo "MEDIA3 VERSION CHECK: FAILED"; printf '%s\n' "$versions"; exit 1; }
grep -q 'dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9' app/build.gradle.kts || { echo "FFMPEG OPTIONAL DEP CHECK: FAILED"; exit 1; }
if grep -R -n -E 'com\.arthenica:ffmpeg-kit-' . --exclude-dir=.gradle --exclude='check-dependencies.sh' >/tmp/openedit_old_ffmpeg.txt 2>/dev/null; then
  cat /tmp/openedit_old_ffmpeg.txt
  echo "OLD FFMPEG COORDINATE CHECK: FAILED"
  exit 1
fi
echo "DEPENDENCY CONSISTENCY: OK"
