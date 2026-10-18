#!/bin/bash
set -euo pipefail

# Fails if the FOSS runtime classpath resolves a dependency that F-Droid's inclusion policy forbids
# (Google Play Services, Firebase, ML Kit, Play Core, billing, consent and tracking SDKs).
# Run after scripts/prepare-foss-build.sh, on the same tree F-Droid builds.

CONFIGURATION="${1:-zcashmainnetFossReleaseRuntimeClasspath}"
FORBIDDEN='com\.google\.android\.gms|com\.google\.firebase|com\.google\.mlkit|com\.google\.android\.odml|com\.google\.android\.play|com\.google\.android\.datatransport|com\.google\.android\.recaptcha|com\.google\.android\.ump|com\.android\.billingclient|com\.android\.installreferrer|io\.sentry|com\.crashlytics|com\.facebook|com\.appsflyer|com\.adjust|com\.mixpanel|com\.amplitude|io\.branch|com\.onesignal|com\.segment'

REPORT="$(mktemp)"
./gradlew -q :app:dependencies --configuration "$CONFIGURATION" > "$REPORT"

if ! grep -q -- '---' "$REPORT"; then
    echo "No dependencies resolved for $CONFIGURATION" >&2
    exit 1
fi

if grep -E "($FORBIDDEN):" "$REPORT"; then
    echo "Non-free dependencies found in $CONFIGURATION" >&2
    exit 1
fi

echo "$CONFIGURATION: no non-free dependencies"
