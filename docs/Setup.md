# Setup Guide
The app is built with Gradle and can be compiled on macOS, Windows, and Linux.  Development is typically done with the latest stable release of Android Studio.

Tip: On macOS and Linux, Gradle is invoked with `./gradlew`.  On Windows, Gradle is invoked with `gradlew`.  Throughout the documentation, the macOS and Linux syntax is used by default.

Tip: You do NOT need to install Gradle yourself.  Running it from the command line or building the application within Android Studio will download the necessary dependencies automatically.

## Step by Step
To get set up for development, there are several steps that you need to go through.  Going through these steps in order is important, as each step in the progression builds on the prior steps.   These steps are written assuming a fresh development environment.

Start by making sure the command line with Gradle works first, because **all the Android Studio run configurations use Gradle internally.**  The run configurations are not magic—they map directly to command line invocations with different arguments.

1. Install Java
    1. Install JVM 17 or greater on your system.  Our setup has been tested with Java 17.  Although a variety of 
       JVM distributions are available and should work, we have settled on recommending [Adoptium/Temurin](https://adoptium.net), because this is the default distribution used by Gradle toolchains.  For Windows or Linux, be sure that the `JAVA_HOME` environment variable points to the right Java version.  Note: If you switch from a newer to an older JVM version, you may see an error like the following `> com.android.ide.common.signing.KeytoolException: Failed to read key AndroidDebugKey from store "~/.android/debug.keystore": Integrity check failed: java.security.NoSuchAlgorithmException: Algorithm HmacPBESHA256 not available`.  A solution is to delete the debug keystore and allow it to be re-generated.
    1. Android Studio has an embedded JVM, although running Gradle tasks from the command line requires a separate JVM to be installed.  Our Gradle scripts are configured to use toolchains to automatically install the correct JVM version.
1. Install Android Studio and the Android SDK
    1. Download [Android Studio](https://developer.android.com/studio/preview).  As of September 2022, we recommend Android Studio Electric Eel preview because it is more robust with Kotlin Multiplatform.  Also note that due to issue #420 Intel-based machines may have trouble building in Android Studio.  If you experience this, the workaround is to add the following line to `~/.gradle/gradle.properties` `ZCASH_IS_DEPENDENCY_LOCKING_ENABLED=false`.
    1. During the Android Studio setup wizard, choose the "Standard" setup option
    1. Note the file path where Android Studio will install the Android developer tools, as you will need this path later
    1. Continue and let Android Studio download and install the rest of the Android developer tools
    1. Do not open the project in Android Studio yet.  That happens in a subsequent step below.  At this stage, we're just using Android Studio to install the Android SDK.
    1. Configure `ANDROID_HOME` environment variable using the path noted above.  This will allow easily running Android development commands, like `adb logcat` or `adb install myapp.apk`
        1. macOS
            1. Add the following to `~/.zprofile`

                ```
                export ANDROID_HOME=THE_PATH_NOTED_ABOVE
                export PATH=${PATH}:${ANDROID_HOME}/tools
                export PATH=${PATH}:${ANDROID_HOME}/tools/bin
                export PATH=${PATH}:${ANDROID_HOME}/platform-tools
                ```

    1. Configure a device for development and testing
        1. Android virtual device
            1. Inside Android Studio, the small More Actions button has an option to open the Virtual Device Manager
            1. When configuring an Android Virtual Device (AVD),  choose an Android version that is within the range of our min and target SDK versions, defined in [gradle.properties](../gradle.properties).
        1. Physical Android device
            1. Enable developer mode
                1. Go into the Android settings
                1. Go to About phone
                1. Tap on the build number seven times (some devices hide this under "Software information")
                1. Go back and navigate to the newly enabled Developer options.  This may be a top-level item or under System > Developer options
                1. Enable USB debugging
                1. Connect your device to your computer, granting permission to the USB MAC address
1. Check out the code.  _Use the command line (instead of Android Studio) to check out the code. This will ensure that your command line environment is set up correctly and avoids a few pitfalls with trying to use Android Studio directly.  Android Studio's built-in git client is not as robust as standalone clients_
    1. To check out a git repo from GitHub, there are three authentication methods: SSH, HTTPS, and GitHub API.  We recommend SSH.
    1. Create a new SSH key, following [GitHub's instructions](https://docs.github.com/en/authentication/connecting-to-github-with-ssh/generating-a-new-ssh-key-and-adding-it-to-the-ssh-agent)
    1. Add the SSH key under [GitHub account settings](https://github.com/settings/keys)
    1. Clone repo in a terminal on your computer `git clone git@github.com:Electric-Coin-Company/zashi-android.git`
1. Compile from the command line
    1. Navigate to the repo checkout in a terminal
    1. Compile the application with the gradle command `./gradlew assemble`
1. Compile from Android Studio
    1. Open Android Studio
    1. From within Android Studio, choose to open an existing project and navigate to the root of the checked out repo.  Point Android Studio to the root of the git repo as (do not point it to the `app` module, as that is just a subset of the project and cannot be opened by itself)
        1. Note: When first opening the project, Android Studio will warn that Gradle checksums are not fully supported.  Choose the "Use checksum" option.  This is a security feature that we have explicitly enabled.
        1. Shortly after opening the project, Android Studio may prompt about updating the Android Gradle Plugin.  DO NOT DO THIS.  If you do so, the build will fail because the project also has dependency locking enabled as a security feature.  To learn more, see [Build integrity.md](Build%20Integrity.md)
        1. Android Studio may prompt about updating the Kotlin plugin.  Do this.  Our application often uses a newer version of Kotlin than is bundled with Android Studio.
    1. After Android Studio finishes syncing with Gradle, select which build variant you'd like your built app to have. From the top menu bar, select Build -> Select Built Variant and then choose from variants of Mainnet or Testnet (for more information see section [Build variants](Setup.md#build-variants) below). After build variant selection, look for the green "play" run button in the toolbar. To the left of it, choose the "app" run configuration under the dropdown menu. Then hit the run button

## Troubleshooting
1. Verify that the Git repo has not been modified.  Due to strict dependency locking (for security reasons), the build will fail unless the locks are also updated
1. Try running from the command line instead of Android Studio, to rule out Android Studio issues.  If it works from the command line, try this step to reset Android Studio
   1. Quit Android Studio
   1. Delete the invisible `.idea` in the root directory of the project.  This directory is partially ignored by Git, so deleting it will remove the files that are untracked
   1. Restore the missing files in `.idea` folder from Git
   1. Relaunch Android Studio
1. Clean the individual Gradle project by running `./gradlew clean` which will purge local build outputs.
1. Run Gradle with the argument `--rerun-tasks` which will effectively disable the build cache by re-running tasks and repopulating the cache.  E.g. `./gradlew assemble --rerun-tasks`
1. Reboot your computer, which will ensure that Gradle and Kotlin daemons are completely killed and relaunched
1. Delete the global Gradle cache under `~/.gradle/caches`
1. If adding a new dependency or updating a dependency, a warning that a dependency cannot be found may indicate the Maven repository restrictions need adjusting
1. If included build (like buildSrc) contains different kotlin version (embedded from gradle) than other modules 
   then Android Studio will not use gradle wrapper version specified in project. Running configurations or using 
   terminal works as expected. [link](https://github.com/Electric-Coin-Company/zashi-android/issues/1495)

## Gradle Tasks
A variety of Gradle tasks are set up within the project, and these tasks are also accessible in Android Studio as run configurations.
 * `assemble` - Compiles the application but does not deploy it
 * `assembleAndroidTest` - Compiles the application and tests, but does not deploy the application or run the tests.  The Android Studio run configuration actually runs all of these tasks because the debug APKs are necessary to run the tests: `assembleDebug assembleZcashmainnetDebug assembleZcashtestnetDebug assembleAndroidTest`
 * `check` - Runs tests of Kotlin-only modules
 * `connectedCheck` - Runs tests of Android-only modules on any running Android virtual device or connected physical Android device 
 * `detektAll` - Performs static analysis with Detekt
 * `ktlintFormat` - Performs code formatting checks with ktlint
 * `lint` - Performs static analysis with Android lint
 * `dependencyUpdates` - Checks for available dependency updates.  It will only suggest final releases, unless a particular dependency is already using a non-final release (e.g. alpha, beta, RC).

A few notes on running instrumentation tests on the `ui-screenshot-test` module:
 - Screenshots are generated automatically and copied to [/ui-screenshot-test/build/output](../ui-screenshot-test/build/outputs)
 - Running the Android tests on the `ui-screenshot-test` module will erase the data stored by the app. This is because Test Orchestrator is required to reset app state to successfully perform integration tests.

Gradle Managed Devices are also configured with our build scripts.  We have found best results running tests one module at a time, rather than trying to run them all at once.  For example: `./gradlew :ui-lib:pixel2TargetDebugAndroidTest` will run the UI tests on a Pixel 2 sized device using our target API version.

## Gradle Properties
A variety of Gradle properties can be used to configure the build.  Most of these properties are optional and help with advanced configuration.  If you're just doing local development or making a small pull request contribution, you likely do not need to worry about these.

### Debug Signing
By default, the application is signed by the developers automatically generated debug signing key.  In a team of developers, it may be advantageous to share a debug key so that debug builds can access key-restricted services such as Firebase or Google Maps.  For such a setup, the path to a shared debug signing key can be set with the property `ZCASH_DEBUG_KEYSTORE_PATH`.

### Release Signing
This section is optional.

To enable release signing, a release keystore needs to be provided during the build.  This can be injected securely by setting the following Gradle properties.
* `ZCASH_RELEASE_KEYSTORE_PATH`
* `ZCASH_RELEASE_KEYSTORE_PASSWORD`
* `ZCASH_RELEASE_KEY_ALIAS`
* `ZCASH_RELEASE_KEY_ALIAS_PASSWORD`

On a developer machine, these might be set under the user's global properties (e.g. `~/.gradle/gradle.properties` on macOS and Linux).  On a continuous integration machine, these can also be set using environment variables with the prefix `ORG_GRADLE_PROJECT_` (e.g. `ORG_GRADLE_PROJECT_ZCASH_RELEASE_KEYSTORE_PATH`).  DO NOT set these in the gradle.properties inside the Git repository, as this will leak your keystore password.

### Build variants
Android apps can have build types (`debug`, `release`), build flavors (`mainnet`, `testnet`), and their combination gives us build variants.  

Debug builds are up to 10x slower due to JIT being disabled by Android's runtime, so they should not be used for benchmarks.  Debug builds also have logging enabled.  By convention, debug builds have a different package name, which means that both a release build from Google Play and a debug build for development can be installed simultaneously.  Another difference is that release builds are processed with R8, which performs "tree shaking" to reduce the size of the final app by stripping unused code and performing code-level optimizations.  This tree shaking process means that release builds have a mapping.txt file that helps reconstruct stacktraces (e.g. crashes).

"mainnet" (main network) and "testnet" (test network) are terms used in the blockchain ecosystem to describe different blockchain networks.  Mainnet is responsible for executing actual transactions within the network and storing them on the blockchain. In contrast, the testnet provides an alternative environment that mimics the mainnet's functionality to allow developers to build and test projects without needing to facilitate live transactions or the use of cryptocurrencies, for example.

Currently, we support 4 build variants for the `app` module: `zcashmainnetDebug`, `zcashtestnetDebug`, `zcashmainnetRelease`, `zcashtestnetRelease`. Library modules like `ui-lib`, `test-lib`, etc. support only `debug` and `release` variants. UI test modules like `ui-integration-test`, `ui-screenshot-test` provide variants extended by the network dimension similarly as app module does. Moreover, the `ui-benchmark-test` introduces a `benchmark` build type, which is supposed to be used only for benchmarking. 

App module build variants:
- `zcashtestnetDebug` - build variant is built upon testnet network and with debug build type. You usually use this variant for development
- `zcashmainnetDebug` - same as previous, but is built upon mainnet network
- `zcashmainnetRelease` and `zcashtestnetRelease` - are usually used by our CI jobs to prepare binaries for testing and releasing to the Google Play Store

### Included builds
This section is optional.

To simplify implementation of Zcash SDK or BIP-39 features in conjunction with changes to the app, a Gradle [Included Build](https://docs.gradle.org/current/userguide/composite_builds.html) can be configured.

1. Check out the SDK 
1. Verify that the `zcash-android-wallet-sdk` builds correctly on its own (e.g. `./gradlew assemble`)
1. In the `secant-android-wallet` repo, modify property `SDK_INCLUDED_BUILD_PATH` to be the absolute path to the `zcash-android-wallet-sdk` checkout.  (You can also use a relative path, but it will be relative to the root of `secant-android-wallet`).  A similar property also exists for BIP-39 `BIP_39_INCLUDED_BUILD_PATH`
1. Build `secant-android-wallet`

There are some limitations of included builds:
1. If `secant-android-wallet` is using a newer version of the Android Gradle plugin compared to `zcash-android-wallet-sdk`, the build will fail.  If this happens, you may need to modify the `zcash-android-wallet-sdk` gradle.properties so that the Android Gradle Plugin version matches that of `secant-android-wallet`.  After making this change, it will be necessary to run a build from the command line with the flag `--write-locks` e.g. `./gradlew assemble --write-locks` in order to update the dependency locks.  Similar problems can occur if projects are using different versions of Kotlin or different versions of Gradle
1. Modules in each project cannot share the same name.  For this reason, build-conventions have different names in each repo (`zcash-android-wallet-sdk/build-conventions` vs `secant-android-wallet/build-conventions-secant`)

### Firebase Test Lab
This section is optional.

For Continuous Integration, see [CI.md](CI.md).  The rest of this section is regarding local development.

1. Configure or request access to a Firebase Test Lab project
    1. If you are an Electric Coin Co team member: Make an IT request to add your Google account to the existing Firebase Test Lab project 
    2. If you are an open source contributor: set up your own Firebase project for the purpose of running Firebase Test Lab
1. Set the Firebase Google Cloud project name as a global Gradle property `ZCASH_FIREBASE_TEST_LAB_PROJECT` under `~/.gradle/gradle.properties`
1. Run the Gradle task `flankAuth` to generate a Firebase authentication token on your machine

Tests can now be run on Firebase Test Lab from your local machine.

The Firebase Test Lab tasks DO NOT build the app, so they rely on existing build outputs.  This means you should:
1. Build the debug and test APKs: `./gradlew assembleDebug assembleZcashmainnetDebug assembleZcashtestnetDebug assembleAndroidTest`
1. Run the tests: `./gradlew runFlank`

## Testnet funds

The Zcash testnet is an alternative blockchain that attempts to mimic the mainnet (main Zcash network) for testing purposes. Testnet coins are distinct from actual ZEC and do not have value. Developers and users can experiment with the testnet without having to use valuable currency. The testnet is also used to test network upgrades and their activation before committing to the upgrade on the main Zcash network. For more information on how to add testnet funds visit [Testnet Guide](https://zcash.readthedocs.io/en/latest/rtd_pages/testnet_guide.html) or go right to the [Testnet Faucet](https://faucet.zecpages.com/).

## Sideloading
Although the goal of this document is to enable readers to build the app from source, it is also possible to 
sideload debug builds created by Continuous Integration. For more details see [Sideloading.md](Sideloading.md).
