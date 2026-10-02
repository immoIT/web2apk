#!/usr/bin/env bash
set -Eeuo pipefail

APK="${1:-app/build/outputs/apk/release/app-release.apk}"

if ! command -v adb >/dev/null 2>&1; then
  echo "ERROR: adb is not installed or not on PATH."
  exit 1
fi
if [[ ! -f "$APK" ]]; then
  echo "ERROR: APK not found: $APK"
  echo "Build it first with the GitHub Actions workflow or Gradle."
  exit 1
fi

echo "APK: $APK"
echo "Connected devices:"
adb devices -l

echo
echo "Installing..."
set +e
OUTPUT="$(adb install -r "$APK" 2>&1)"
STATUS=$?
set -e
printf '%s\n' "$OUTPUT"

if (( STATUS != 0 )); then
  echo
  echo "Installation failed. The line beginning with 'Failure' is the real cause."
  echo "Useful diagnostics:"
  echo "  adb shell getprop ro.product.cpu.abi"
  echo "  adb shell getprop ro.build.version.sdk"
  echo "  adb shell pm path com.example.websitetopk"
  echo "  adb logcat -d -t 300 | grep -iE 'PackageManager|INSTALL_FAILED|avc|WebView'"
  exit "$STATUS"
fi

echo "Installation succeeded."
