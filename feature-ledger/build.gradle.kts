import model.DistributionDimension
import model.NetworkDimension

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("secant.android-build-conventions")
    id("secant.jacoco-conventions")
}

android {
    namespace = "co.electriccoin.zcash.ledger"

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.androidx.compose.compiler.get().versionConstraint.displayName
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
}

dependencies {
    implementation(projects.uiLib)

    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core)
    implementation(libs.bundles.androidx.compose.core)
    implementation(libs.bundles.androidx.compose.extended)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.immutable)
    implementation(libs.kotlinx.serializable.json)
    implementation(libs.zcash.sdk.incubator)

    implementation(projects.buildInfoLib)
    implementation(projects.configurationApiLib)
    implementation(projects.crashAndroidLib)
    implementation(projects.preferenceApiLib)
    implementation(projects.preferenceImplAndroidLib)
    implementation(projects.spackleAndroidLib)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
