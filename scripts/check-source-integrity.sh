#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

grep -RIn --exclude-dir=build --exclude='*.md' 'animatedTransform(' app/src/main/java && {
  echo "ERROR: stale animatedTransform() reference found"; exit 1;
} || true

grep -RIn --exclude-dir=build --exclude='*.md' 'state\.effects\.rotation, state\.effects\.contrast' app/src/main/java && {
  echo "ERROR: obsolete preview effects call found"; exit 1;
} || true

grep -RIn --exclude-dir=build --exclude='*.md' 'effectsForClip(' app/src/main/java && {
  echo "ERROR: obsolete private effectsForClip() reference found"; exit 1;
} || true

if grep -RIn --exclude-dir=build --exclude='check-source-integrity.sh' 'com.arthenica:ffmpeg-kit' .; then
  echo "ERROR: retired FFmpegKit Maven coordinate found"
  exit 1
fi

echo "SOURCE INTEGRITY: OK"
