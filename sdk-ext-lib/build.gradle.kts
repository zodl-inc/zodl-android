plugins {
    id("com.android.library")
    kotlin("android")
    id("secant.android-build-conventions")
    id("secant.jacoco-conventions")
}

android {
    namespace = "cash.z.ecc.sdk.ext"
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    api(enforcedPlatform(libs.ktor.bom))
    api(libs.ktor.core)
    api(libs.ktor.okhttp)
    api(libs.ktor.negotiation)
    api(libs.ktor.json)
    api(libs.ktor.logging)

    api(libs.zcash.sdk)
    api(libs.zcash.sdk.incubator)
    api(libs.zcash.bip39)
    api(libs.zip321)

    androidTestImplementation(libs.bundles.androidx.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlin.test)

    androidTestUtil(libs.androidx.test.services) {
        artifact {
            type = "apk"
        }
    }

    if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
        androidTestUtil(libs.androidx.test.orchestrator) {
            artifact {
                type = "apk"
            }
        }
    }
}
