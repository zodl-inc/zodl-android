# Flexa Android SDK (vendored)

The `core` and `spend` modules of the Flexa Android SDK, built from source as the Gradle projects
`:flexa-core` and `:flexa-spend` instead of the `co.flexa:core` / `co.flexa:spend` Maven artifacts.

- Upstream: https://github.com/flexa/flexa-android, tag `1.1.3`, commit
  `e83f3923923a20ff877fe9858f1b14defe093363` (`FLEXA_VERSION` in `gradle.properties`).
- License: MIT (`LICENSE.md`, `NOTICE.txt`), unchanged.
- `core/src/main` and `spend/src/main` are upstream's, with `patches/0001-okhttp-client-customizer.patch`
  applied; `core/src/test` holds that patch's test. Nothing else from upstream is taken (the example app,
  the `scan` module, upstream's build scripts).
- The patch adds `FlexaClientConfiguration.okHttpClientCustomizer`, applied to every OkHttp client the SDK
  builds for the Flexa API, after the SDK's own interceptors. The app uses it to send the Flexa API
  calls through the OHTTP gateway (`FlexaOhttpInterceptor` in ui-lib).
- `build.gradle.kts` replaces upstream's Groovy build files: the dependencies and versions are those of
  the published 1.1.3 runtime variants; `RELEASE_DATE` is the upstream release date instead of the build
  date, and `DEBUG_API_HOST` is always null, as in upstream's release build.

To update: check out the new upstream tag, apply the patch, copy `core/src/main` and `spend/src/main`
here, align the dependency lists with the new published `.module` files, and update `FLEXA_VERSION`,
`RELEASE_DATE` and the commit above.
