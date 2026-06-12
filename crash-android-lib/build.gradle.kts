import model.DistributionDimension
import model.NetworkDimension

plugins {
    id("com.android.library")
    kotlin("android")
    id("secant.android-build-conventions")
    id("secant.jacoco-conventions")
}

// Note that we force enable test orchestrator for this module, because some of the test cases require it.
// Specifically this is needed due to checks on the UncaughtExceptionHandler tests

android {
    namespace = "co.electriccoin.zcash.crash.android"
    testNamespace = "co.electriccoin.zcash.crash.test"

    defaultConfig {
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    flavorDimensions += listOf(NetworkDimension.DIMENSION_NAME, DistributionDimension.DIMENSION_NAME)

    productFlavors {
        create(NetworkDimension.TESTNET.value) {
            dimension = NetworkDimension.DIMENSION_NAME
        }

        create(NetworkDimension.MAINNET.value) {
            dimension = NetworkDimension.DIMENSION_NAME
        }

        create(DistributionDimension.STORE.value) {
            dimension = DistributionDimension.DIMENSION_NAME
        }

        create(DistributionDimension.FOSS.value) {
            dimension = DistributionDimension.DIMENSION_NAME
        }

        create(DistributionDimension.INTERNAL.value) {
            dimension = DistributionDimension.DIMENSION_NAME
        }
    }

    sourceSets {
        getByName("internal").apply {
            kotlin.srcDirs("src/internal/kotlin")
        }
    }
}

dependencies {
    api(libs.androidx.annotation)
    api(projects.crashLib)

    api(libs.bundles.koin)

    "storeImplementation"(platform(libs.firebase.bom))
    "storeImplementation"(libs.firebase.crashlytics)
    "storeImplementation"(libs.firebase.crashlytics.ndk)
    "storeImplementation"(libs.firebase.installations)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(projects.spackleAndroidLib)

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
