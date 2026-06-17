plugins {
    id("com.android.library")
    kotlin("android")
    id("secant.android-build-conventions")
    id("secant.jacoco-conventions")
}

android {
    namespace = "co.electriccoin.zcash.spackle"
    testNamespace = "co.electriccoin.zcash.spackle.test"
    // Force orchestrator to be used for this module, because we need the process name to be purged between tests
    defaultConfig {
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

dependencies {
    api(projects.spackleLib)
    implementation(libs.androidx.annotation)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    androidTestImplementation(libs.bundles.androidx.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    androidTestUtil(libs.androidx.test.services) {
        artifact {
            type = "apk"
        }
    }

    androidTestUtil(libs.androidx.test.orchestrator) {
        artifact {
            type = "apk"
        }
    }
}
