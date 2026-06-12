# Continuous Integration
Continuous integration is set up with GitHub Actions.  The workflows are defined in this repo under [/.github/workflows](../.github/workflows).

Workflows exist for:
 * Pull request - On pull request, static analysis and testing is performed.
 * Deploy - On merge to the main branch, a release build is automatically deployed.  Concurrency limits are in place, to ensure that only one release deployment can happen at a time.

## Setup
When forking this repository, some variables/secrets need to be defined to set up new continuous integration builds.

The variables/secrets passed to GitHub Actions then map to Gradle properties set up within our build scripts.  Necessary secrets are documented at the top of each GitHub workflow yml file, as well as reiterated here.

To enhance security, [OpenID Connect](https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-google-cloud-platform) is used to generate temporary access tokens for each build.

### Pull request
* Variables
    * `FIREBASE_TEST_LAB_PROJECT` - Firebase Test Lab project name.
* Secrets
    * `FIREBASE_TEST_LAB_SERVICE_ACCOUNT` - Email address of Firebase Test Lab service account.
    * `FIREBASE_TEST_LAB_WORKLOAD_IDENTITY_PROVIDER` - Workload identity provider to generate temporary service account key.
    * `FIREBASE_DEBUG_JSON_BASE64` - Base64 encoded google-services.json file for enabling Firebase services such as Crashlytics.
    * `FIREBASE_RELEASE_JSON_BASE64` - Base64 encoded google-services.json file for enabling Firebase services such as Crashlytics.

The Pull Request workflow supports testing of the app and libraries via Firebase Test Lab (configured) and a fallback local emulator (reactivecircus/android-emulator-runner GitHub Action).

To configure Firebase Test Lab, you'll need to enable the necessary Google Cloud APIs to enable automated access to Firebase Test Lab.
* Configure Firebase Test Lab.  Google has [documentation for Jenkins](https://firebase.google.com/docs/test-lab/android/continuous).  Although we're using GitHub Actions, the initial requirements are the same.
* Configure [workload identity federation](https://github.com/google-github-actions/auth#setting-up-workload-identity-federation)

Note that pull requests will create a "release" build with a temporary fake signing key.  This simplifies configuration of CI for forks who simply want to run tests and not do release deployments.  The limitations of this approach are:
 - These builds cannot be used for testing of app upgrade compatibility (since signing key is different each time)
 - Firebase, Google Play Services, and Google Maps won't work since they use the signing key to restrict API access.  The app does not currently use any services with signature checks but this could become an issue in the future.

Note that `FIREBASE_DEBUG_JSON_BASE64` and `FIREBASE_RELEASE_JSON_BASE64` are not truly considered secret, as they contain API keys that are embedded in the application.  However we are not including them in the repository to reduce accidental pollution of our crash report data from repository forks.

### Release deployment
* Secrets
    * `GOOGLE_PLAY_CLOUD_PROJECT` - Google Cloud project associated with Google Play.
    * `GOOGLE_PLAY_SERVICE_ACCOUNT` - Email address of service account.
    * `GOOGLE_PLAY_WORKLOAD_IDENTITY_PROVIDER` - Workload identity provider to generate temporary service account key
    * `UPLOAD_KEYSTORE_BASE_64` — Base64 encoded upload keystore.
    * `UPLOAD_KEYSTORE_PASSWORD` — Password for upload keystore.
    * `UPLOAD_KEY_ALIAS` — Name of key inside upload keystore.
    * `UPLOAD_KEY_ALIAS_PASSWORD` — Password for key alias.
    * `FIREBASE_DEBUG_JSON_BASE64` - Base64 encoded google-services.json file for enabling Firebase services such as Crashlytics.
    * `FIREBASE_RELEASE_JSON_BASE64` - Base64 encoded google-services.json file for enabling Firebase services such as Crashlytics.

To obtain the values for the Google Play deployment, you'll need to

* Create a service account with access to your Google Play account.  Recommended permissions are to "edit and delete draft apps" and "release apps to testing tracks".
* Configure [workload identity federation](https://github.com/google-github-actions/auth#setting-up-workload-identity-federation)

Note that security of release deployments is enhanced via two mechanisms:
 - CI signs the app with the upload keystore and not the final release keystore.  If the upload keystore is ever leaked, it can be rotated without impacting end user security.
 - Deployment to Google Play can only be made to testing tracks.  Release to production requires manual human login under a different account with greater permissions.

Note that `FIREBASE_DEBUG_JSON_BASE64` and `FIREBASE_RELEASE_JSON_BASE64` are not truly considered secret, as they contain API keys that are embedded in the application.  However we are not including them in the repository to reduce accidental pollution of our crash report data from repository forks.