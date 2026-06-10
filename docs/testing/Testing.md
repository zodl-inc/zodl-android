# Testing
This documentation outlines our approach to testing. By running tests against our app consistently, we verify the app's correctness, functional behavior, and usability before releasing it publicly.

## Automated testing

- TBD
<!-- TODO [#682]: Testing documentation update --> 
<!-- TODO [#682]: https://github.com/Electric-Coin-Company/zashi-android/issues/682 --> 

## Manual testing

We aim to automate as much as we possibly can. Still manual testing is really important for Quality Assurance.

Here you'll find our manual testing scripts. When developing a new feature you can add your own that provide the proper steps to properly test it.

## Gathering Code Coverage
The app consists of different Gradle module types (e.g. Kotlin Multiplatform, Android).  Generating coverage for these different module types requires different command line invocations.

### Kotlin Multiplatform
Kotlin Multiplatform does not support coverage for all platforms.  Most of our code lives under commonMain, with a JVM target.  This effectively allows generation of coverage reports with Jacoco.  Coverage is enabled by default when running `./gradlew check`.

### Android
The Android Gradle plugin supports code coverage with Jacoco.  This integration can sometimes be buggy.  For that reason, coverage is disabled by default and can be enabled on a case-by-case basis, by setting the property `IS_ANDROID_INSTRUMENTATION_TEST_COVERAGE_ENABLED=true`.  For example: `./gradlew connectedCheck -PIS_ANDROID_INSTRUMENTATION_TEST_COVERAGE_ENABLED=true`.

When coverage is enabled, running instrumentation tests will automatically generate coverage reports stored under `$module/build/reports/coverage`.

### Generating code coverage locally
- `./gradlew <android-module-name>:connectedCheck -PIS_ANDROID_INSTRUMENTATION_TEST_COVERAGE_ENABLED=true` for Android modules. These modules internally use JaCoCo to generate test coverage. You can then find the coverage report in `<android-module-name>/build/reports/coverage/androidTest/debug/connected/index.html`.
- `./gradlew <kotlin-only-module-name>:check` for Kotlin modules. These modules internally use Kover to generate test coverage. You can then find the coverage report in `<kotlin-only-module-name>/build/kover/html/index.html`. 

## Benchmarking
This section provides information about available benchmark tests integrated in this project as well as how to use them. Currently, we support macrobenchmark tests run locally as described in the Android [documentation](https://developer.android.com/topic/performance/benchmarking/benchmarking-overview).

We provide dedicated benchmark test module `ui-benchamark-test` for this. If you want to run these benchmark tests against our application, make sure you have a physical device connected with Android SDK level 29, at least. Select `benchmark` build variant for this module. Make sure that other modules are set to release variants of their available build variants too, as benchmarking is only allowed against minified build variants. The benchmark tests can be run with Android Studio run configuration `ui-benchmark-test:connectedBenchmarkAndroidTest` with having the Gradle property `IS_SIGN_RELEASE_BUILD_WITH_DEBUG_KEY` set to true. Running the benchmark test this way automatically provides benchmarking results in Run panel. Or you can run the tests manually from the terminal with `./gradlew connectedBenchmarkAndroidTest -PIS_SIGN_RELEASE_BUILD_WITH_DEBUG_KEY=true` and analyze results with Android Studio's Profiler or [Perfetto](https://ui.perfetto.dev/) tool, as described in this Android [documentation](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview#access-trace).

**Note**: We've enabled benchmarking also for emulators, although it's always better to run the tests on a real physical device. Emulator benchmark improvements might not carry over to a real user's experience (or even regress real device performance).

### Referential benchmark tests results
Every few months, or before a major app release, we run and compare benchmark test results to avoid making the app's mechanisms significantly slower.

**Note**: If possible, run the benchmark tests on a physical device with sufficient empty disk space, connected to the
internet and charged or plugged-in to a charger. It's always better to restart the device before approaching to
running the benchmark tests. Also, please, ensure you're running it on the latest main branch
commits of that date. Generate tests results with the Android Studio run configuration
`ui-benchmark-test:connectedBenchmarkAndroidTest` and gather results from the Run panel.

#### Dec 7, 2022:

- APP version: `0.1 (1)`
- SDK version: `1.10.0-beta01-SNAPSHOT`
- Git branch: `682-testing-doc-update`
- Device:
    - Pixel 6 - Android 13:
      ```
      Starting 1 tests on Pixel 6 - 13

      BasicStartupBenchmark_startup
      timeToFullDisplayMs   min 288.1,   median 305.6,   max 339.1
      timeToInitialDisplayMs   min 288.1,   median 305.6,   max 339.1
      Traces: Iteration 0 1 2 3 4
      
      BUILD SUCCESSFUL in 32s
      ```
    - Pixel 3a - Android 12:
      ```
      Starting 1 tests on Pixel 3a - 12

      BasicStartupBenchmark_startup
      timeToFullDisplayMs   min 708.3,   median 763.1,   max 770.9
      timeToInitialDisplayMs   min 708.3,   median 763.1,   max 770.9
      Traces: Iteration 0 1 2 3 4
      
      BUILD SUCCESSFUL in 41s
      ```

#### Feb 27, 2023:

- APP version: `0.1 (1)`
- SDK version: `1.14.0-beta01-SNAPSHOT`
- Git branch: `main`
- Device:
    - Pixel 6 - Android 13:
      ```
      Starting 1 tests on Pixel 6 - 13
    
      BasicStartupBenchmark_startup
      timeToFullDisplayMs   min 259.2,   median 265.3,   max 302.4
      timeToInitialDisplayMs   min 259.2,   median 265.3,   max 302.4
      Traces: Iteration 0 1 2 3 4
    
      BUILD SUCCESSFUL in 1m 59s
      ```
    - Pixel 3a - Android 12:
      ```
      Starting 1 tests on Pixel 3a - 12
    
      BasicStartupBenchmark_startup
      timeToFullDisplayMs   min 632.2,   median 730.8,   max 785.6
      timeToInitialDisplayMs   min 632.2,   median 730.8,   max 785.6
      Traces: Iteration 0 1 2 3 4
    
      BUILD SUCCESSFUL in 43s
      ```