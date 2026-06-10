import model.DistributionDimension
import model.BuildType
import model.NetworkDimension

plugins {
    id("com.android.test")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("secant.android-build-conventions")
}

// Force orchestrator to be used for this module, because we need cleared state to generate screenshots
val isOrchestratorEnabled = true

android {
    namespace = "co.electroniccoin.zcash.ui.screenshot"
    // Target needs to be set to com.android.application type module
    targetProjectPath = ":${projects.app.name}"
    // Run tests in this module
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        if (isOrchestratorEnabled) {
            testInstrumentationRunnerArguments["clearPackageData"] = "true"
        }

        testInstrumentationRunner = "co.electroniccoin.zcash.ui.screenshot.ZcashScreenshotTestRunner"
    }

    // Define the same flavors as in app module
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
    buildTypes {
        create(BuildType.RELEASE.value) {
            // To provide compatibility with other modules
        }
    }

    if (isOrchestratorEnabled) {
        testOptions {
            execution = "ANDROIDX_TEST_ORCHESTRATOR"
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.androidx.compose.compiler.get().versionConstraint.displayName
    }
}

dependencies {
    implementation(projects.configurationApiLib)
    implementation(projects.configurationImplAndroidLib)
    implementation(projects.sdkExtLib)
    implementation(projects.spackleAndroidLib)
    implementation(projects.testLib)
    implementation(projects.uiLib)

    implementation(libs.bundles.androidx.test)
    implementation(libs.bundles.androidx.compose.core)

    implementation(libs.androidx.compose.test.junit)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.startup)
    implementation(libs.androidx.uiAutomator)

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

emulatorwtf {
    directoriesToPull.set(listOf("/sdcard/googletest/test_outputfiles"))

    // Because screenshot tests can be flaky, allow this module to always re-run
    // which is helpful on GitHub Actions.  Once the tests are fully stabilized, this can be
    // removed.
    sideEffects.set(true)
}
