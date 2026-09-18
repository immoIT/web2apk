#!/usr/bin/env bash
# Load config/defaults.properties. Existing non-empty environment variables
# (including GitHub Actions vars) take precedence.
set -Eeuo pipefail

DEFAULTS_FILE="${DEFAULTS_FILE:-config/defaults.properties}"

if [[ -f "$DEFAULTS_FILE" ]]; then
  while IFS='=' read -r key value; do
    key="$(echo "$key" | xargs)"
    [[ -z "$key" || "$key" == \#* ]] && continue
    value="${value%$'\r'}"
    if [[ -z "${!key:-}" ]]; then
      export "$key=$value"
    fi
  done < "$DEFAULTS_FILE"
fi

export ALLOW_EXTERNAL_LINKS="${ALLOW_EXTERNAL_LINKS:-true}"
export ENABLE_CUSTOM_SCRIPTS="${ENABLE_CUSTOM_SCRIPTS:-true}"
