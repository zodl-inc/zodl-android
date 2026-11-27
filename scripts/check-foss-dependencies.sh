#!/bin/bash
set -euo pipefail

# Fails if the FOSS runtime classpath resolves a dependency that F-Droid's inclusion policy forbids
# (Google Play Services, Firebase, ML Kit, Play Core, billing, consent and tracking SDKs).
# Run after scripts/prepare-foss-build.sh, on the same tree F-Droid builds.
# Usage: check-foss-dependencies.sh [configuration] | --self-test

FORBIDDEN='com\.google\.android\.gms|com\.google\.firebase|com\.google\.mlkit|com\.google\.android\.odml|com\.google\.android\.play|com\.google\.android\.datatransport|com\.google\.android\.recaptcha|com\.google\.android\.ump|com\.android\.billingclient|com\.android\.installreferrer|io\.sentry|com\.crashlytics|com\.facebook\.android|com\.appsflyer|com\.adjust|com\.mixpanel|com\.amplitude|io\.branch|com\.onesignal|com\.segment'

# Group ids are matched as a prefix ending at '.' or ':', so subgroups such as
# com.segment.analytics.android are caught too.
find_forbidden() {
    grep -E "(^|[^A-Za-z0-9_.])($FORBIDDEN)[.:]"
}

self_test() {
    local bad=(
        '+--- com.google.firebase:firebase-crashlytics:19.0.0'
        '|    \--- com.google.android.gms:play-services-base:18.5.0'
        '+--- com.google.mlkit:barcode-scanning:17.3.0'
        '+--- com.google.android.play:review:2.0.2'
        '\--- com.segment.analytics.android:analytics:4.11.3'
        '+--- io.sentry:sentry-android:7.0.0'
        '+--- com.facebook.android:facebook-core:17.0.0'
    )
    local good=(
        '+--- com.google.zxing:core:3.5.3'
        '+--- io.github.zxing-cpp:android:2.3.0'
        '+--- com.google.android.material:material:1.12.0'
        '+--- com.google.crypto.tink:tink-android:1.17.0'
        '+--- com.facebook.fresco:fresco:3.4.0'
        '+--- co.flexa:core:1.1.3'
        '+--- org.example.com.google.firebase.fake:lib:1.0'
    )
    local line
    for line in "${bad[@]}"; do
        printf '%s\n' "$line" | find_forbidden > /dev/null || { echo "self-test: not flagged: $line" >&2; exit 1; }
    done
    for line in "${good[@]}"; do
        if printf '%s\n' "$line" | find_forbidden > /dev/null; then
            echo "self-test: wrongly flagged: $line" >&2
            exit 1
        fi
    done
    echo "self-test passed"
}

if [ "${1:-}" = "--self-test" ]; then
    self_test
    exit 0
fi

CONFIGURATION="${1:-zcashmainnetFossReleaseRuntimeClasspath}"

REPORT="$(mktemp)"
./gradlew -q :app:dependencies --configuration "$CONFIGURATION" > "$REPORT"

if ! grep -q -- '---' "$REPORT"; then
    echo "No dependencies resolved for $CONFIGURATION" >&2
    exit 1
fi

if find_forbidden < "$REPORT"; then
    echo "Non-free dependencies found in $CONFIGURATION" >&2
    exit 1
fi

echo "$CONFIGURATION: no non-free dependencies"
