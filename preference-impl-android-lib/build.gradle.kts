plugins {
    id("com.android.library")
    kotlin("android")
    id("secant.android-build-conventions")
    id("secant.jacoco-conventions")
}

// Force orchestrator to be used for this module, because we need the preference files
// to be purged between tests
val isOrchestratorEnabled = true

android {
    namespace = "co.electriccoin.zcash.preference"

    if (isOrchestratorEnabled) {
        defaultConfig {
            testInstrumentationRunnerArguments["clearPackageData"] = "true"
        }

        testOptions {
            execution = "ANDROIDX_TEST_ORCHESTRATOR"
        }
    }
}

dependencies {
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(projects.preferenceApiLib)

    androidTestImplementation(libs.bundles.androidx.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    androidTestUtil(libs.androidx.test.services) {
        artifact {
            type = "apk"
        }
    }

    if (isOrchestratorEnabled) {
        androidTestUtil(libs.androidx.test.orchestrator) {
            artifact {
                type = "apk"
            }
        }
    }
}
