# Flexa Android SDK (vendored)

The `core` and `spend` modules of the Flexa Android SDK, built from source as the Gradle projects
`:flexa-core` and `:flexa-spend` instead of the `co.flexa:core` / `co.flexa:spend` Maven artifacts.

**Maintainer:** y4ssi
**Automation:** `.github/workflows/flexa-sdk-update.yml` checks daily for new releases and opens
update PRs automatically.

- Upstream: https://github.com/flexa/flexa-android, tag `1.1.3`, commit
  `e83f3923923a20ff877fe9858f1b14defe093363` (`FLEXA_VERSION` in `gradle.properties`).
- License: MIT (`LICENSE.md`, `NOTICE.txt`), unchanged.
- `core/src/main` and `spend/src/main` are upstream's, with the patches in `patches/` applied in order;
  `core/src/test` holds their tests. Nothing else from upstream is taken (the example app,
  the `scan` module, upstream's build scripts).
- The patch adds `FlexaClientConfiguration.okHttpClientCustomizer`, applied to every OkHttp client the SDK
  builds for the Flexa API, after the SDK's own interceptors. The app uses it to send the Flexa API
  calls through the OHTTP gateway (`FlexaOhttpInterceptor` in ui-lib).
- `0002-direct-connection-gate.patch` adds `FlexaClientConfiguration.directConnectionsAllowed`, asked before
  the SDK connects to Flexa outside the API clients: images (one Coil `FlexaImageLoader` whose OkHttp client
  refuses downloads while it is false, so the placeholder shows) and web views (`DirectConnectionConsent`
  shows a disclosure and loads the page only after the user agrees). The app returns false on a private
  connection or with Tor on (`FlexaRepositoryImpl`).
- `0003-redact-authorization-in-debug-logs.patch` redacts `Authorization` in the SDK's debug HTTP logs.
- `0004-consent-follows-privacy-mode.patch` makes `DirectConnectionConsent` ask `Flexa.allowsDirectConnections()` while
  it is on screen: a web view loads by itself when the app allows direct connections. An agreement the user gives
  while they are off is not saved and ends when they turn off while the page is shown or the screen is left. Before
  `Flexa.init` ran nothing is allowed (`Flexa.configured`), so a restored process does not connect directly.
- `build.gradle.kts` replaces upstream's Groovy build files: the dependencies and versions are those of
  the published 1.1.3 runtime variants; `RELEASE_DATE` is the upstream release date instead of the build
  date, and `DEBUG_API_HOST` is always null, as in upstream's release build.

## Why the vendored copy and patch

`OkHttpProvider` (the SDK's internal HTTP client builder) is `internal`, so a host app has no public hook
to change the clients the SDK builds. The app needs one to send the Flexa API calls through OHTTP. With
such a hook upstream, the Maven artifacts would do: F-Droid accepts libraries from Maven Central, as it
does for the wallet SDK.

We decided (2026-10-19) not to open an upstream PR to flexa/flexa-android for now. The patch is small and
Flexa releases are rare, so keeping it here costs little; an upstream PR would also make public that our
wallet proxies Flexa traffic. If keeping the copy current becomes a burden, propose the hook upstream as a
generic "customize the OkHttp clients" option.

The patch files are `patches/0001-okhttp-client-customizer.patch`, `0002-direct-connection-gate.patch` and
`0003-redact-authorization-in-debug-logs.patch` (git format-patch format, paths relative to the upstream root).

## Update procedure for a new Flexa release

1. Check out the new upstream tag: `git clone https://github.com/flexa/flexa-android && cd flexa-android && git checkout <tag>`.
2. Apply the patches in order: `for p in /path/to/this/repo/third-party/flexa/patches/*.patch; do git am < "$p" || break; done`.
   If one doesn't apply cleanly, resolve conflicts, `git add`, `git am --continue`, apply the rest, and
   regenerate the patches with `git format-patch <upstream tag>`.
3. Copy `core/src/main` and `spend/src/main` here, overwriting the old code.
4. Align the dependency lists in `build.gradle.kts` with the new published `.module` files
   (check Maven Central or the upstream tag's `core/build.gradle` / `spend/build.gradle`).
5. Update `FLEXA_VERSION`, `RELEASE_DATE` (from the upstream release), and the commit SHA in this README.
6. Verify: CI builds the app; the "Check FOSS dependencies" (`scripts/check-foss-dependencies.sh`, run on
   the FOSS release runtime classpath by the `build` job of `pull-request.yml`) and "F-Droid scanner" steps
   must pass, and `third-party/flexa` must contain no binaries.
