import com.android.build.api.variant.BuildConfigField
import model.DistributionDimension
import model.NetworkDimension

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("secant.android-build-conventions")
    id("wtf.emulator.gradle")
    id("secant.emulator-wtf-conventions")
    id("secant.jacoco-conventions")
}

android {
    namespace = "co.electriccoin.zcash.ui"

    defaultConfig {
        testInstrumentationRunner = "co.electriccoin.zcash.test.ZcashUiTestRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.androidx.compose.compiler.get().versionConstraint.displayName
    }

    sourceSets {
        getByName("main").apply {
            res.setSrcDirs(
                setOf(
                    // This is a special case as these texts are not translated, they are replaced in build time via
                    // app/build.gradle.kts instead
                    "src/main/res/ui/non_translatable",

                    "src/main/res/ui/about",
                    "src/main/res/ui/account_list",
                    "src/main/res/ui/address_book",
                    "src/main/res/ui/add_contact",
                    "src/main/res/ui/advanced_settings",
                    "src/main/res/ui/authentication",
                    "src/main/res/ui/balances",
                    "src/main/res/ui/common",
                    "src/main/res/ui/contact",
                    "src/main/res/ui/connect_keystone",
                    "src/main/res/ui/crash_reporting_opt_in",
                    "src/main/res/ui/delete_wallet",
                    "src/main/res/ui/export_data",
                    "src/main/res/ui/error",
                    "src/main/res/ui/home",
                    "src/main/res/ui/insufficient_funds",
                    "src/main/res/ui/choose_server",
                    "src/main/res/ui/integrations",
                    "src/main/res/ui/onboarding",
                    "src/main/res/ui/pay",
                    "src/main/res/ui/payment_request",
                    "src/main/res/ui/qr_code",
                    "src/main/res/ui/request",
                    "src/main/res/ui/receive",
                    "src/main/res/ui/review_keystone_transaction",
                    "src/main/res/ui/restore",
                    "src/main/res/ui/restore_success",
                    "src/main/res/ui/scan",
                    "src/main/res/ui/scan_keystone",
                    "src/main/res/ui/security_warning",
                    "src/main/res/ui/seed_recovery",
                    "src/main/res/ui/select_keystone_account",
                    "src/main/res/ui/send",
                    "src/main/res/ui/send_confirmation",
                    "src/main/res/ui/settings",
                    "src/main/res/ui/sign_keystone_transaction",
                    "src/main/res/ui/swap",
                    "src/main/res/ui/transaction_detail",
                    "src/main/res/ui/transaction_filters",
                    "src/main/res/ui/transaction_history",
                    "src/main/res/ui/transaction_note",
                    "src/main/res/ui/tax_export",
                    "src/main/res/ui/tex_unsupported",
                    "src/main/res/ui/feedback",
                    "src/main/res/ui/update",
                    "src/main/res/ui/update_contact",
                    "src/main/res/ui/wallet_address",
                    "src/main/res/ui/warning",
                    "src/main/res/ui/whats_new",
                    "src/main/res/ui/exchange_rate",
                    "src/main/res/ui/tor",
                )
            )
        }
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
    }
}

androidComponents {
    onVariants { variant ->
        // Configure SecureScreen for protecting screens with sensitive data in runtime
        variant.buildConfigFields?.put(
            "IS_SECURE_SCREEN_ENABLED",
            BuildConfigField(
                type = "boolean",
                value = project.property("IS_SECURE_SCREEN_PROTECTION_ACTIVE").toString(),
                comment = "Whether is the SecureScreen sensitive data protection enabled"
            )
        )
        variant.buildConfigFields?.put(
            "ZCASH_FLEXA_KEY",
            BuildConfigField(
                type = "String",
                value = "\"${project.property("ZCASH_FLEXA_KEY")?.toString().orEmpty()}\"",
                comment = "Publishable key of the Flexa integration"
            )
        )
        variant.buildConfigFields?.put(
            "ZCASH_CMC_KEY",
            BuildConfigField(
                type = "String",
                value = "\"${project.property("ZCASH_CMC_KEY")?.toString().orEmpty()}\"",
                comment = "Publishable key of the CMC integration"
            )
        )
        // To configure screen orientation in runtime
        variant.buildConfigFields?.put(
            "IS_SCREEN_ROTATION_ENABLED",
            BuildConfigField(
                type = "boolean",
                value = project.property("IS_SCREEN_ROTATION_ENABLED").toString(),
                comment = "Whether is the screen rotation enabled, otherwise, it's locked in the portrait mode"
            )
        )
    }
}

dependencies {
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.splash)
    implementation(libs.androidx.workmanager)
    implementation(libs.androidx.browser)
    implementation(libs.bundles.androidx.camera)
    implementation(libs.bundles.androidx.compose.core)
    implementation(libs.bundles.androidx.compose.extended)
    api(libs.bundles.koin)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.immutable)
    implementation(libs.kotlinx.serializable.json)
    "storeImplementation"(libs.mlkit.scanning)
    api(libs.zcash.sdk)
    implementation(libs.zcash.sdk.incubator)
    implementation(libs.zcash.bip39)
    implementation(libs.tink)
    implementation(libs.zxing)

    api(libs.flexa.core)
    api(libs.flexa.spend)

    implementation(projects.buildInfoLib)
    implementation(projects.configurationApiLib)
    implementation(projects.crashAndroidLib)
    implementation(projects.preferenceApiLib)
    implementation(projects.preferenceImplAndroidLib)
    implementation(projects.spackleAndroidLib)
    api(projects.configurationImplAndroidLib)
    api(projects.sdkExtLib)
    api(projects.uiDesignLib)
    api(libs.androidx.fragment)
    api(libs.androidx.fragment.compose)
    api(libs.androidx.activity)
    api(libs.bundles.androidx.biometric)

    api(libs.keystone)

    androidTestImplementation(projects.testLib)
    androidTestImplementation(libs.bundles.androidx.test)
    androidTestImplementation(libs.androidx.compose.test.junit)
    androidTestImplementation(libs.androidx.compose.test.manifest)
    androidTestImplementation(libs.kotlin.reflect)
    androidTestImplementation(libs.kotlin.test)

    androidTestUtil(libs.androidx.test.services) {
        artifact {
            type = "apk"
        }
    }

    if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
        androidTestUtil(libs.androidx.test.services)
        androidTestUtil(libs.androidx.test.orchestrator) {
            artifact {
                type = "apk"
            }
        }
    }
}
