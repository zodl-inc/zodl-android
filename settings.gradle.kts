enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        val isRepoRestrictionEnabled = true

        val googleGroups = listOf(
            "androidx.navigation",
            "com.android",
            "com.google.testing.platform",
            "com.google.firebase",
            "com.google.firebase.appdistribution"
        )
        val googleRegexes = listOf(
            "androidx\\..*",
            "com\\.android(\\.|\\:).*"
        )
        val wtfGroups = listOf("wtf.emulator")

        mavenCentral {
            if (isRepoRestrictionEnabled) {
                content {
                    wtfGroups.forEach {
                        includeGroup(it)
                    }
                }
            }
        }
        google {
            if (isRepoRestrictionEnabled) {
                content {
                    googleGroups.forEach {
                        includeGroup(it)
                    }
                    googleRegexes.forEach {
                        includeGroupByRegex(it)
                    }
                }
            }
        }
        gradlePluginPortal {
            if (isRepoRestrictionEnabled) {
                content {
                    (wtfGroups + googleGroups).forEach {
                        excludeGroup(it)
                    }
                    googleRegexes.forEach {
                        excludeGroupByRegex(it)
                    }
                }
            }
        }
    }

    plugins {
        val androidGradlePluginVersion = extra["ANDROID_GRADLE_PLUGIN_VERSION"].toString()
        val kotlinVersion = extra["KOTLIN_VERSION"].toString()

        id("com.android.application") version (androidGradlePluginVersion) apply false
        id("com.android.library") version (androidGradlePluginVersion) apply false
        id("com.android.test") version (androidGradlePluginVersion) apply false
        id("com.google.firebase.appdistribution") version (extra["FIREBASE_APP_DISTRIBUTION_GRADLE_PLUGIN_VERSION"].toString()) apply false
        id("com.github.ben-manes.versions") version (extra["GRADLE_VERSIONS_PLUGIN_VERSION"].toString()) apply false
        id("com.osacky.fulladle") version (extra["FULLADLE_VERSION"].toString()) apply false
        id("org.jetbrains.kotlinx.kover") version (extra["KOVER_VERSION"].toString()) apply false
        id("org.jetbrains.kotlin.plugin.compose") version (kotlinVersion) apply (false)
        id("wtf.emulator.gradle") version (extra["EMULATOR_WTF_GRADLE_PLUGIN_VERSION"].toString()) apply false
        kotlin("android") version (kotlinVersion) apply false
        kotlin("jvm") version (kotlinVersion) apply false
        kotlin("multiplatform") version (kotlinVersion) apply false
        kotlin("plugin.serialization") version (kotlinVersion) apply false
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    @Suppress("UnstableApiUsage")
    repositories {
        val isRepoRestrictionEnabled = true

        val googleGroups = listOf(
            "androidx.benchmark",
            "androidx.navigation",
            "com.android",
            "com.google.android.apps.common.testing.accessibility.framework",
            "com.google.android.datatransport",
            "com.google.android.gms",
            "com.google.android.material",
            "com.google.android.odml",
            "com.google.android.play",
            "com.google.firebase",
            "com.google.mlkit",
            "com.google.testing.platform",
        )
        val googleRegexes = listOf(
            "androidx\\..*",
            "com\\.android(\\.|\\:).*",
        )
        val wtfGroups = listOf("wtf.emulator")

        google {
            if (isRepoRestrictionEnabled) {
                content {
                    googleGroups.forEach {
                        includeGroup(it)
                    }
                    googleRegexes.forEach {
                        includeGroupByRegex(it)
                    }
                }
            }
        }
        mavenCentral {
            if (isRepoRestrictionEnabled) {
                content {
                    (wtfGroups + googleGroups).forEach {
                        excludeGroup(it)
                    }
                    googleRegexes.forEach {
                        excludeGroupByRegex(it)
                    }
                }
            }
        }
        maven("https://oss.sonatype.org/content/repositories/snapshots") {
            mavenContent {
                snapshotsOnly()
            }
            if (isRepoRestrictionEnabled) {
                content {
                    includeGroup("cash.z.ecc.android")
                }
            }
        }
        maven("https://central.sonatype.com/repository/maven-snapshots") {
            mavenContent {
                snapshotsOnly()
            }
            if (isRepoRestrictionEnabled) {
                content {
                    includeGroup("cash.z.ecc.android")
                }
            }
        }
        // start wtf maven
        maven("https://maven.emulator.wtf/releases/") {
            if (isRepoRestrictionEnabled) {
                content {
                    includeGroup("wtf.emulator")
                }
            }
        }
        // end wtf maven
        maven("https://jitpack.io")
    }

    @Suppress("MaxLineLength")
    versionCatalogs {
        create("libs") {
            val accompanistPermissionsVersion = extra["ACCOMPANIST_PERMISSIONS_VERSION"].toString()
            val androidxActivityVersion = extra["ANDROIDX_ACTIVITY_VERSION"].toString()
            val androidxAnnotationVersion = extra["ANDROIDX_ANNOTATION_VERSION"].toString()
            val androidxBiometricVersion = extra["ANDROIDX_BIOMETRIC_VERSION"].toString()
            val androidxCameraVersion = extra["ANDROIDX_CAMERA_VERSION"].toString()
            val androidxComposeCompilerVersion = extra["ANDROIDX_COMPOSE_COMPILER_VERSION"].toString()
            val androidxComposeMaterial3Version = extra["ANDROIDX_COMPOSE_MATERIAL3_VERSION"].toString()
            val androidxComposeMaterialIconsVersion = extra["ANDROIDX_COMPOSE_MATERIAL_ICONS_VERSION"].toString()
            val androidxComposeVersion = extra["ANDROIDX_COMPOSE_VERSION"].toString()
            val androidxConstraintLayoutVersion = extra["ANDROIDX_CONSTRAINTLAYOUT_VERSION"].toString()
            val androidxCoreVersion = extra["ANDROIDX_CORE_VERSION"].toString()
            val androidxEspressoVersion = extra["ANDROIDX_ESPRESSO_VERSION"].toString()
            val androidxLifecycleVersion = extra["ANDROIDX_LIFECYCLE_VERSION"].toString()
            val androidxFragmentVersion = extra["ANDROIDX_FRAGMENT_VERSION"].toString()
            val androidxNavigationComposeVersion = extra["ANDROIDX_NAVIGATION_COMPOSE_VERSION"].toString()
            val androidxProfileInstallerVersion = extra["ANDROIDX_PROFILE_INSTALLER_VERSION"].toString()
            val androidxSecurityCryptoVersion = extra["ANDROIDX_SECURITY_CRYPTO_VERSION"].toString()
            val androidxSplashScreenVersion = extra["ANDROIDX_SPLASH_SCREEN_VERSION"].toString()
            val androidxStartupVersion = extra["ANDROIDX_STARTUP_VERSION"].toString()
            val androidxTestCoreVersion = extra["ANDROIDX_TEST_CORE_VERSION"].toString()
            val androidxTestJunitVersion = extra["ANDROIDX_TEST_JUNIT_VERSION"].toString()
            val androidxTestMacrobenchmarkVersion = extra["ANDROIDX_TEST_MACROBENCHMARK_VERSION"].toString()
            val androidxTestOrchestratorVersion = extra["ANDROIDX_TEST_ORCHESTRATOR_VERSION"].toString()
            val androidxTestServices = extra["ANDROIDX_TEST_SERVICE_VERSION"].toString()
            val androidxTestRunnerVersion = extra["ANDROIDX_TEST_RUNNER_VERSION"].toString()
            val androidxUiAutomatorVersion = extra["ANDROIDX_UI_AUTOMATOR_VERSION"].toString()
            val androidxWorkManagerVersion = extra["ANDROIDX_WORK_MANAGER_VERSION"].toString()
            val androidxBrowserVersion = extra["ANDROIDX_BROWSER_VERSION"].toString()
            val coreLibraryDesugaringVersion = extra["CORE_LIBRARY_DESUGARING_VERSION"].toString()
            val flankVersion = extra["FLANK_VERSION"].toString()
            val jacocoVersion = extra["JACOCO_VERSION"].toString()
            val javaVersion = extra["ANDROID_JVM_TARGET"].toString()
            val kotlinVersion = extra["KOTLIN_VERSION"].toString()
            val kotlinxDateTimeVersion = extra["KOTLINX_DATETIME_VERSION"].toString()
            val kotlinxCoroutinesVersion = extra["KOTLINX_COROUTINES_VERSION"].toString()
            val kotlinxImmutableCollectionsVersion = extra["KOTLINX_IMMUTABLE_COLLECTIONS_VERSION"].toString()
            val kotlinxSerializableJsonVersion = extra["KOTLINX_SERIALIZABLE_JSON_VERSION"].toString()
            val lottieVersion = extra["LOTTIE_VERSION"].toString()
            val markdownVersion = extra["MARKDOWN_VERSION"].toString()
            val mlkitScanningVersion = extra["MLKIT_SCANNING_VERSION"].toString()
            val tinkVersion = extra["TINK_VERSION"].toString()
            val zcashBip39Version = extra["ZCASH_BIP39_VERSION"].toString()
            val zcashSdkVersion = extra["ZCASH_SDK_VERSION"].toString()
            val zip321Version = extra["ZIP_321_VERSION"].toString()
            val zxingVersion = extra["ZXING_VERSION"].toString()
            val koinVersion = extra["KOIN_VERSION"].toString()
            val flexaVersion = extra["FLEXA_VERSION"].toString()
            val keystoneVersion = extra["KEYSTONE_VERSION"].toString()
            val shimmerVersion = extra["SHIMMER_VERSION"].toString()
            val ktorVersion = extra["KTOR_VERSION"].toString()

            // Standalone versions
            version("flank", flankVersion)
            version("jacoco", jacocoVersion)
            version("java", javaVersion)

            // Aliases
            library("accompanist-permissions", "com.google.accompanist:accompanist-permissions:$accompanistPermissionsVersion")
            library("androidx-activity", "androidx.activity:activity-ktx:$androidxActivityVersion")
            library("androidx-activity-compose", "androidx.activity:activity-compose:$androidxActivityVersion")
            library("androidx-annotation", "androidx.annotation:annotation:$androidxAnnotationVersion")
            library("androidx-biometric", "androidx.biometric:biometric:$androidxBiometricVersion")
            library("androidx-biometric-ktx", "androidx.biometric:biometric-ktx:$androidxBiometricVersion")
            library("androidx-camera", "androidx.camera:camera-camera2:$androidxCameraVersion")
            library("androidx-camera-lifecycle", "androidx.camera:camera-lifecycle:$androidxCameraVersion")
            library("androidx-camera-view", "androidx.camera:camera-view:$androidxCameraVersion")
            library("androidx-compose-foundation", "androidx.compose.foundation:foundation:$androidxComposeVersion")
            library("androidx-compose-material3", "androidx.compose.material3:material3:$androidxComposeMaterial3Version")
            library("androidx-compose-material-icons-core", "androidx.compose.material:material-icons-core:$androidxComposeMaterialIconsVersion")
            library("androidx-compose-material-icons-extended", "androidx.compose.material:material-icons-extended:$androidxComposeMaterialIconsVersion")
            library("androidx-compose-tooling", "androidx.compose.ui:ui-tooling:$androidxComposeVersion")
            library("androidx-compose-ui", "androidx.compose.ui:ui:$androidxComposeVersion")
            library("androidx-compose-ui-fonts", "androidx.compose.ui:ui-text-google-fonts:$androidxComposeVersion")
            library("androidx-compose-compiler", "androidx.compose.compiler:compiler:$androidxComposeCompilerVersion")
            library("androidx-core", "androidx.core:core-ktx:$androidxCoreVersion")
            library("androidx-constraintlayout", "androidx.constraintlayout:constraintlayout-compose:$androidxConstraintLayoutVersion")
            library("androidx-fragment", "androidx.fragment:fragment:$androidxFragmentVersion")
            library("androidx-fragment-compose", "androidx.fragment:fragment-compose:$androidxFragmentVersion")
            library("androidx-lifecycle-livedata", "androidx.lifecycle:lifecycle-livedata-ktx:$androidxLifecycleVersion")
            library("androidx-lifecycle-compose", "androidx.lifecycle:lifecycle-runtime-compose:$androidxLifecycleVersion")
            library("androidx-lifecycle-process", "androidx.lifecycle:lifecycle-process:$androidxLifecycleVersion")
            library("androidx-navigation-compose", "androidx.navigation:navigation-compose:$androidxNavigationComposeVersion")
            library("androidx-profileinstaller", "androidx.profileinstaller:profileinstaller:$androidxProfileInstallerVersion")
            library("androidx-security-crypto", "androidx.security:security-crypto-ktx:$androidxSecurityCryptoVersion")
            library("androidx-splash", "androidx.core:core-splashscreen:$androidxSplashScreenVersion")
            library("androidx-startup", "androidx.startup:startup-runtime:$androidxStartupVersion")
            library("androidx-viewmodel-compose", "androidx.lifecycle:lifecycle-viewmodel-compose:$androidxLifecycleVersion")
            library("androidx-workmanager", "androidx.work:work-runtime-ktx:$androidxWorkManagerVersion")
            library("androidx-browser", "androidx.browser:browser:$androidxBrowserVersion")
            library("desugaring", "com.android.tools:desugar_jdk_libs:$coreLibraryDesugaringVersion")
            library("firebase-bom", "com.google.firebase:firebase-bom:${extra["FIREBASE_BOM_VERSION_MATCHER"]}")
            library("firebase-installations", "com.google.firebase", "firebase-installations").withoutVersion()
            library("firebase-crashlytics", "com.google.firebase", "firebase-crashlytics-ktx").withoutVersion()
            library("firebase-crashlytics-ndk", "com.google.firebase", "firebase-crashlytics-ndk").withoutVersion()
            library("kotlin-stdlib", "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
            library("kotlin-reflect", "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
            library("kotlin-test", "org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
            library("kotlinx-coroutines-android", "org.jetbrains.kotlinx:kotlinx-coroutines-android:$kotlinxCoroutinesVersion")
            library("kotlinx-coroutines-core", "org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
            library("kotlinx-coroutines-guava", "org.jetbrains.kotlinx:kotlinx-coroutines-guava:$kotlinxCoroutinesVersion")
            library("kotlinx-datetime", "org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDateTimeVersion")
            library("kotlinx-immutable", "org.jetbrains.kotlinx:kotlinx-collections-immutable:$kotlinxImmutableCollectionsVersion")
            library("kotlinx-serializable-json", "org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializableJsonVersion")
            library("lottie", "com.airbnb.android:lottie-compose:$lottieVersion")
            library("markdown", "org.jetbrains:markdown:$markdownVersion")
            library("mlkit-scanning", "com.google.mlkit:barcode-scanning:$mlkitScanningVersion")
            library("tink", "com.google.crypto.tink:tink-android:$tinkVersion")
            library("zcash-sdk", "cash.z.ecc.android:zcash-android-sdk:$zcashSdkVersion")
            library("zcash-sdk-incubator", "cash.z.ecc.android:zcash-android-sdk-incubator:$zcashSdkVersion")
            library("zcash-bip39", "cash.z.ecc.android:kotlin-bip39:$zcashBip39Version")
            library("zip321", "org.zecdev:zip321:$zip321Version")
            library("zxing", "com.google.zxing:core:$zxingVersion")
            library("koin", "io.insert-koin:koin-android:$koinVersion")
            library("koin-compose", "io.insert-koin:koin-androidx-compose:$koinVersion")
            library("flexa-core", "co.flexa:core:$flexaVersion")
            library("flexa-spend", "co.flexa:spend:$flexaVersion")
            library("keystone", "com.github.KeystoneHQ:keystone-sdk-android:$keystoneVersion")
            library("compose-shimmer", "com.valentinilk.shimmer:compose-shimmer:$shimmerVersion")
            library("ktor-core", "io.ktor:ktor-client-core:$ktorVersion")
            library("ktor-okhttp", "io.ktor:ktor-client-okhttp:$ktorVersion")
            library("ktor-negotiation", "io.ktor:ktor-client-content-negotiation:$ktorVersion")
            library("ktor-json", "io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
            library("ktor-logging", "io.ktor:ktor-client-logging:$ktorVersion")

            // Test libraries
            library("androidx-compose-test-junit", "androidx.compose.ui:ui-test-junit4:$androidxComposeVersion")
            library("androidx-compose-test-manifest", "androidx.compose.ui:ui-test-manifest:$androidxComposeVersion")
            // Cannot use espresso-contrib, because it causes a build failure
            //alias("androidx-espresso-contrib", "androidx.test.espresso:espresso-contrib:$androidxEspressoVersion")
            library("androidx-espresso-core", "androidx.test.espresso:espresso-core:$androidxEspressoVersion")
            library("androidx-espresso-intents", "androidx.test.espresso:espresso-intents:$androidxEspressoVersion")
            library("androidx-test-core", "androidx.test:core-ktx:$androidxTestCoreVersion")
            library("androidx-test-junit", "androidx.test.ext:junit-ktx:$androidxTestJunitVersion")
            library("androidx-test-macrobenchmark", "androidx.benchmark:benchmark-macro-junit4:$androidxTestMacrobenchmarkVersion")
            library("androidx-test-orchestrator", "androidx.test:orchestrator:$androidxTestOrchestratorVersion")
            library("androidx-test-runner", "androidx.test:runner:$androidxTestRunnerVersion")
            library("androidx-test-services","androidx.test.services:test-services:$androidxTestServices")
            library("androidx-uiAutomator", "androidx.test.uiautomator:uiautomator:$androidxUiAutomatorVersion")
            library("kotlinx-coroutines-test", "org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinxCoroutinesVersion")
            // Bundles
            bundle(
                "androidx-biometric",
                listOf(
                    "androidx-biometric",
                    "androidx-biometric-ktx",
                )
            )
            bundle(
                "androidx-camera",
                listOf(
                    "androidx-camera",
                    "androidx-camera-lifecycle",
                    "androidx-camera-view"
                )
            )
            bundle(
                "androidx-compose-core",
                listOf(
                    "androidx-compose-foundation",
                    "androidx-compose-material3",
                    "androidx-compose-tooling",
                    "androidx-compose-ui",
                    "androidx-compose-ui-fonts"
                )
            )
            bundle(
                "androidx-compose-extended",
                listOf(
                    "androidx-activity-compose",
                    "androidx-compose-material-icons-core",
                    "androidx-compose-material-icons-extended",
                    "androidx-lifecycle-compose",
                    "androidx-navigation-compose",
                    "androidx-viewmodel-compose"
                )
            )
            bundle(
                "androidx-test",
                listOf(
                    "androidx-espresso-core",
                    "androidx-espresso-intents",
                    "androidx-test-core",
                    "androidx-test-junit",
                    "androidx-test-orchestrator",
                    "androidx-test-runner"
                )
            )
            bundle(
                "koin",
                listOf(
                    "koin",
                    "koin-compose",
                )
            )
        }
    }
}

rootProject.name = "zcash-android-app"

includeBuild("build-conventions-secant")

include("app")
include("build-info-lib")
include("configuration-api-lib")
include("configuration-impl-android-lib")
include("crash-lib")
include("crash-android-lib")
include("preference-api-lib")
include("preference-impl-android-lib")
include("sdk-ext-lib")
include("spackle-lib")
include("spackle-android-lib")
include("test-lib")
include("ui-benchmark-test")
include("ui-design-lib")
include("ui-integration-test")
include("ui-lib")
include("ui-screenshot-test")

val zcashSdkIncludedBuildPath = extra["SDK_INCLUDED_BUILD_PATH"].toString()

if (zcashSdkIncludedBuildPath.isNotEmpty()) {
    logger.lifecycle("The SDK will be used from $zcashSdkIncludedBuildPath instead of Maven Central.")
    includeBuild(zcashSdkIncludedBuildPath) {
        dependencySubstitution {
            substitute(module("cash.z.ecc.android:zcash-android-sdk")).using(project(":sdk-lib"))
            substitute(module("cash.z.ecc.android:zcash-android-sdk-incubator")).using(project(":sdk-incubator-lib"))
        }
    }
}

val bip39IncludedBuildPath = extra["BIP_39_INCLUDED_BUILD_PATH"].toString()

if (bip39IncludedBuildPath.isNotEmpty()) {
    logger.lifecycle("BIP-39 will be used from $bip39IncludedBuildPath instead of Maven Central.")
    includeBuild(bip39IncludedBuildPath) {
        dependencySubstitution {
            substitute(module("cash.z.ecc.android:kotlin-bip39")).using(project(":bip39-lib"))
        }
    }
}
