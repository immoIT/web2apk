#!/usr/bin/env bash
set -Eeuo pipefail

APK="${1:-}"
if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "Usage: $0 path/to/app.apk" >&2
  exit 2
fi

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_ROOT" || ! -d "$SDK_ROOT" ]]; then
  echo "ANDROID_HOME/ANDROID_SDK_ROOT is not set to an Android SDK." >&2
  exit 2
fi

APKSIGNER="$(find "$SDK_ROOT/build-tools" -type f -name apksigner -perm -111 2>/dev/null | sort -V -r | head -n1 || true)"
APKANALYZER="$(command -v apkanalyzer 2>/dev/null || true)"
if [[ -z "$APKANALYZER" ]]; then
  APKANALYZER="$(find "$SDK_ROOT/cmdline-tools" -type f -name apkanalyzer -perm -111 2>/dev/null | sort -V -r | head -n1 || true)"
fi

[[ -x "$APKSIGNER" ]] || { echo "apksigner not found in $SDK_ROOT/build-tools" >&2; exit 1; }

echo "=== APK ==="
ls -lh "$APK"
echo "=== Signature ==="
"$APKSIGNER" verify --verbose "$APK"
"$APKSIGNER" verify --print-certs "$APK"

if [[ -n "$APKANALYZER" ]]; then
  echo "=== Package/version ==="
  "$APKANALYZER" manifest application-id "$APK"
  "$APKANALYZER" manifest version-name "$APK"
  "$APKANALYZER" manifest version-code "$APK"
  echo "=== TV manifest/features ==="
  "$APKANALYZER" manifest print "$APK" | grep -E 'uses-feature|LEANBACK_LAUNCHER|LAUNCHER|android:banner|android:required' || true
fi
