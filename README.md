# Zashi Android Wallet

This is the official home of the Zashi Zcash wallet for Android, a no-frills
Zcash mobile wallet leveraging the [Zcash Android SDK](https://github.com/Electric-Coin-Company/zcash-android-wallet-sdk).  

# Download

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/co.electriccoin.zcash.foss/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=co.electriccoin.zcash)

Or download the latest APK from the [Releases Section](https://github.com/Electric-Coin-Company/zashi-android/releases/latest).

# Zashi Discord

Join the Zashi community on ECC Discord server, report bugs, share ideas, request new features, and help shape Zashi's journey!

https://discord.gg/jQPU7aXe7A

# Reporting an issue

If you'd like to report a technical issue or feature request for the Android
Wallet, please file a GitHub issue [here](https://github.com/Electric-Coin-Company/zashi-android/issues/new/choose).

For feature requests and issues related to the Zashi user interface that are
not Android-specific, please file a GitHub issue [here](https://github.com/Electric-Coin-Company/zashi/issues/new/choose).

If you wish to report a security issue, please follow our 
[Responsible Disclosure guidelines](https://github.com/Electric-Coin-Company/zashi/blob/master/responsible_disclosure.md).
See the [Wallet App Threat Model](https://github.com/Electric-Coin-Company/zashi/blob/master/wallet_threat_model.md) 
for more information about the security and privacy limitations of the wallet.

General Zcash questions and/or support requests and are best directed to either:
 * [Zcash Forum](https://forum.zcashcommunity.com/)
 * [Discord Community](https://discord.io/zcash-community)

# Contributing

Contributions are very much welcomed!  Please read our [Contributing Guidelines](docs/CONTRIBUTING.md) to learn about our process.

# Getting Started

If you'd like to compile this application from source, please see our [Setup Documentation](docs/Setup.md) to get started.

# Forking

If you plan to fork the project to create a new app of your own, please make
the following changes.  (If you're making a GitHub fork to contribute back to
the project, these steps are not necessary.)

1. Change the app name under [gradle.properties](gradle.properties)
    1. See `ZCASH_RELEASE_APP_NAME`
1. Change the package name under [app/build.gradle.kts](app/build.gradle.kts)
    1. See `ZCASH_RELEASE_PACKAGE_NAME`
1. Change the support email address under [strings.xml](ui-lib/src/main/res/ui/non_translatable/values/strings.xml)
    1. See `support_email_address`
1. Remove any copyrighted ZCash icons, logos, or assets
    1. ui-lib/src/main/res/common/ - All of the the ic_launcher assets
1. Optional
    1. Configure secrets and variables for [Continuous Integration](docs/CI.md)
    1. Configure Firebase API keys and place them under `app/src/debug/google-services.json` and `app/src/release/google-services.json`

# Known Issues

1. During builds, a warning will be printed that says "Unable to detect AGP
   versions for included builds. All projects in the build should use the same
   AGP version."  This can be safely ignored.  The version under
   build-conventions is the same as the version used elsewhere in the
   application.
1. When the code coverage Gradle property
   `IS_ANDROID_INSTRUMENTATION_TEST_COVERAGE_ENABLED` is enabled, the debug app
   APK cannot be run.  The coverage flag should therefore only be set when
   running automated tests.
1. Test coverage for Compose code will be low, due to [known limitations](https://github.com/jacoco/jacoco/issues/1208) in the interaction between Compose and Jacoco.
1. Adding the `espresso-contrib` dependency will cause builds to fail, due to conflicting classes.  This is a [known issue](https://github.com/Electric-Coin-Company/zcash-android-wallet-sdk/issues/306) with the Zcash Android SDK.
1. During app first launch, the following exception starting with `AndroidKeysetManager: keyset not found, will generate a new one` is printed twice.  This exception is not an error, and the code is not being invoked twice.
