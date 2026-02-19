#!/bin/bash
set -e

# Script to prepare FOSS build by removing non-FOSS components
# This script is used by CI to strip proprietary dependencies before building FOSS variants

echo "Removing Google Services JSON files..."
rm -f app/src/debug/google-services.json
rm -f app/src/release/google-services.json

echo "Cleaning build directories..."
rm -rf build-conventions-secant/build/
rm -rf build-conventions-secant/.gradle/
rm -rf buildSrc/build/
rm -rf buildSrc/.gradle/
rm -rf ui-screenshot-test
rm -rf .gradle

echo "Removing emulator.wtf conventions..."
rm -f build-conventions-secant/src/main/kotlin/secant.emulator-wtf-conventions.gradle.kts

echo "Stripping emulator.wtf from settings.gradle.kts..."
sed -i '/\/\/ start wtf maven/,/\/\/ end wtf maven/d' settings.gradle.kts

echo "Removing emulator.wtf references from build files..."
find . -type f -name "build.gradle.kts" -exec sed -i -e '/wtf.emulator.gradle/d' {} +
find . -type f -name "build.gradle.kts" -exec sed -i -e '/secant.emulator-wtf-conventions/d' {} +

echo "Cleaning gradle lockfiles..."
find . -type f -name "gradle.lockfile" -exec sed -i -e '/wtf.emulator/d' {} +
find . -type f -name "buildscript-gradle.lockfile" -exec sed -i -e '/wtf.emulator/d' {} +
find . -type f -name "gradle.lockfile" -exec sed -i -e '/com.vdurmont/d' {} +
find . -type f -name "buildscript-gradle.lockfile" -exec sed -i -e '/com.vdurmont/d' {} +
find . -type f -name "gradle.lockfile" -exec sed -i -e '/org.json:json/d' {} +
find . -type f -name "buildscript-gradle.lockfile" -exec sed -i -e '/org.json:json/d' {} +
find . -type f -name "gradle.lockfile" -exec sed -i -e '/io.sweers.autotransient/d' {} +
find . -type f -name "gradle.lockfile" -exec sed -i -e '/com.ryanharter.auto.value/d' {} +
find . -type f -name "buildscript-gradle.lockfile" -exec sed -i -e '/io.sweers.autotransient/d' {} +
find . -type f -name "buildscript-gradle.lockfile" -exec sed -i -e '/com.ryanharter.auto.value/d' {} +

echo "Removing ui-screenshot-test from settings..."
sed -i -e '/include("ui-screenshot-test")/d' settings.gradle.kts

echo "Removing Google dependencies from build files..."
sed -i -e '/com.google.gms/d' -e '/com.google.android.gms/d' -e '/com.google.firebase/d' -e '/crashlyticsVersion/d' build.gradle.kts
sed -i -e '/libs.google.services/d' -e '/libs.firebase/d' build.gradle.kts
sed -i -e '/com.google.gms/d' -e '/com.google.android.gms/d' -e '/com.google.firebase/d' buildscript-gradle.lockfile
sed -i -e '/libs.google.services/d' -e '/libs.firebase/d' buildscript-gradle.lockfile
# Remove Google Services, Crashlytics and App Distribution from build scripts
sed -i -e '/com.google.gms.google-services/d' -e '/com.google.firebase.crashlytics/d' -e '/com.google.firebase.appdistribution/d' */build.gradle.kts

# Remove App Distribution configuration blocks and master task
sed -i -e '/firebaseAppDistribution {/,/}/d' */build.gradle.kts
sed -i -e '/tasks.register("deployAllVariants") {/,/}/d' */build.gradle.kts

echo "FOSS build preparation completed successfully"
