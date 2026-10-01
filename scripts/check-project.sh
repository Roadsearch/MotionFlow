#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if command -v gradle >/dev/null 2>&1; then
  gradle :app:test :app:assembleDebug
elif [[ -x ./gradlew ]]; then
  ./gradlew :app:test :app:assembleDebug
else
  echo "No Gradle launcher found. Open the project in Android Studio or install Gradle 9.6/JDK 17." >&2
  exit 2
fi
