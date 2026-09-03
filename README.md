# Zodl Android Wallet

This is the official home of the Zodl Zcash wallet for Android, a no-frills
Zcash mobile wallet leveraging the [Zcash Android SDK](https://github.com/zcash/zcash-android-wallet-sdk).

# Download

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://foss.zodl.com)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=co.electriccoin.zcash)

Zodl runs its own F-Droid repository at <https://foss.zodl.com>. To add it to
your F-Droid client, tap this link on Android:

```
fdroidrepos://foss.zodl.com/fdroid/repo?fingerprint=7e751ab710159dff44f55631f910ba4033f0ebd2f867691af633cece4ddb62e4
```

…or add the URL manually in **F-Droid → Settings → Repositories → +**:
`https://foss.zodl.com/fdroid/repo` (fingerprint
`7e751ab710159dff44f55631f910ba4033f0ebd2f867691af633cece4ddb62e4`).

Unlike the `f-droid.org` build, our repo ships the same build-time
integrations as the Google Play release (Flexa, CMC, Crashlytics) and is
signed with the same upload key — no reinstall needed when switching.

Or download the latest APK from the [Releases Section](https://github.com/zodl-inc/zodl-android/releases/latest).

## APK signing

Every APK you download yourself — the GitHub Releases assets, the
foss.zodl.com repo, and the f-droid.org build (reproducible, so f-droid.org
ships our developer-signed APK) — carries the same ZODL release key (the
"upload key" in CI terms). The Google Play listing goes through Play App
Signing, so a copy installed from Google Play may carry Google's app-signing
certificate instead — see `docs/Sideloading.md`. You can verify a downloaded
APK with:

```sh
apksigner verify --print-certs app-zcashmainnet-foss-release.apk
```

**Expected certificate fingerprints:**

| Algorithm | Fingerprint |
|-----------|-------------|
| SHA-256 | `412a1d2412a0be5ffbad9e7dd1eeedb9442dad8b9e9dffc334c07a0c3645ddfe` |
| SHA-1 | `7bd525dc69da6b1fbfe91488b98329f82d55c0f5` |
| MD5 | `33d742aafb8b57a35169cc6b527fec31` |

**Certificate DN:** `C=01, ST=CO, L=Denver, O=Zerocash Electric Coin Company, OU=Core Engineering`
*(Note: the DN reflects the original key created under the ECC/Zashi name — the signing key has not been rotated as part of the ZODL rebrand. `C=01` is what the certificate actually contains, not a standard ISO-3166 code. The SHA-256 fingerprint above is the canonical identifier for verification.)*

The same values live in [`apk-signatures.json`](apk-signatures.json); the
release workflow refuses to publish an APK whose certificate does not match
that file, so the two cannot silently drift apart.

Each GitHub release includes GPG signatures (`.asc` files) for all APKs, signed
with the ZODL GPG key (fingerprint below). The public key is attached to every
release as `zodl-gpg-public-key.asc` (releases published before this file was
added lack it — take it from a newer release). You can verify the APK
authenticity:

```sh
# Download the APK, its signature and the public key from GitHub releases
wget https://github.com/zodl-inc/zodl-android/releases/latest/download/app-zcashmainnet-foss-release.apk
wget https://github.com/zodl-inc/zodl-android/releases/latest/download/app-zcashmainnet-foss-release.apk.asc
wget https://github.com/zodl-inc/zodl-android/releases/latest/download/zodl-gpg-public-key.asc

# Import the ZODL GPG key and check that its fingerprint matches the one below
gpg --import zodl-gpg-public-key.asc
gpg --fingerprint 033834DD49DECF9DBB9934BC6C93CA8E58E26AB1

# Verify signature
gpg --verify app-zcashmainnet-foss-release.apk.asc app-zcashmainnet-foss-release.apk
```

**ZODL GPG key:**
- Fingerprint: `0338 34DD 49DE CF9D BB99  34BC 6C93 CA8E 58E2 6AB1`
- Signing subkey: `1FE9 9324 758F 2967 18B4  5706 7F4B BBBA 23F0 617F` (this is the key id `gpg --verify` prints)
- Email: `sysadmin@zodl.com`
- Also listed on [keys.openpgp.org](https://keys.openpgp.org/search?q=033834DD49DECF9DBB9934BC6C93CA8E58E26AB1)
  for cross-checking the fingerprint only: the identity there is not verified
  yet, so `gpg --recv-keys` returns no importable key — import from the
  release asset as shown above.

### Obtainium

Add Zodl to [Obtainium](https://github.com/ImranR98/Obtainium) using this
source URL:

```
https://github.com/zodl-inc/zodl-android
```

Set **APK filter** to `app-zcashmainnet-foss-release.apk`. That is the build
f-droid.org reproduces (no Flexa/CMC integrations); use
`app-zcashmainnet-foss-release-full-externallibs.apk` instead if you want the
integrations foss.zodl.com serves — same package, same signing key.

Obtainium does not check certificate fingerprints itself. To verify the
signature before installing, install
[Verified Apps](https://github.com/privacyguides/verified-apps-android) and
enable **Share new apps with "Verified Apps"** in Obtainium's settings, then
compare the SHA-256 fingerprint it shows against the table above. Zodl is not
in the Verified Apps database yet, so it reports the app as unknown together
with its fingerprint; the manual comparison is what verifies it.

# Zodl Support

Obtain help for Zodl and connect with our team at [support.zodl.com](https://support.zodl.com/).

# Reporting an issue

If you'd like to report a technical issue or feature request for the Android
Wallet, please file a GitHub issue [here](https://github.com/zodl-inc/zodl-android/issues/new/choose).

For feature requests and issues related to the Zodl user interface that are
not Android-specific, please file a GitHub issue [here](https://github.com/zodl-inc/zodl-project/issues/new/choose).

If you wish to report a security issue, please follow our
[Responsible Disclosure guidelines](https://github.com/zodl-inc/zodl-project/blob/master/responsible_disclosure.md).
See the [Wallet App Threat Model](https://github.com/zodl-inc/zodl-project/blob/master/wallet_threat_model.md)
for more information about the security and privacy limitations of the wallet.

General Zcash questions and/or support requests and are best directed to either:
 * [Zcash Forum](https://forum.zcashcommunity.com/)
 * [Discord Community](https://discord.io/zcash-community)

# Contributing

Contributions are very much welcomed!  Please read our 
[Contributing Guidelines](docs/CONTRIBUTING.md) to learn about our process.

# Getting Started

If you'd like to compile this application from source, please see our 
[Setup Documentation](docs/Setup.md) to get started.

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
1. Remove any copyrighted Zcash icons, logos, or assets
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
1. Adding the `espresso-contrib` dependency will cause builds to fail, due to conflicting classes.  This is a [known issue](https://github.com/zcash/zcash-android-wallet-sdk/issues/306) with the Zcash Android SDK.
1. During app first launch, the following exception starting with `AndroidKeysetManager: keyset not found, will generate a new one` is printed twice.  This exception is not an error, and the code is not being invoked twice.
