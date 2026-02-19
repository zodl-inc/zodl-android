import co.electriccoin.zcash.Git
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.ResValue
import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import model.BuildType
import model.DistributionDimension
import model.NetworkDimension
import java.util.Locale

plugins {
    id("com.android.application")
    kotlin("android")
    id("secant.android-build-conventions")
    id("com.osacky.fladle")
    id("wtf.emulator.gradle")
    id("secant.emulator-wtf-conventions")
    id("publish.secant.publish-conventions")
    id("com.google.firebase.appdistribution")
}

val hasFirebaseApiKeys = run {
    val srcDir = File(project.projectDir, "src")
    val releaseApiKey = File(File(srcDir, "release"), "google-services.json")
    val debugApiKey = File(File(srcDir, "debug"), "google-services.json")
    val fossApiKey = File(File(srcDir, "foss"), "google-services.json")

    val result = releaseApiKey.exists() || debugApiKey.exists() || fossApiKey.exists()

    if (!result) {
        project.logger.info("Firebase API keys not found. Crashlytics will not be enabled. To enable " +
            "Firebase, add the API keys for ${releaseApiKey.path}, ${debugApiKey.path} or ${fossApiKey.path}."
        )
    }

    result
}

if (hasFirebaseApiKeys) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val packageName = project.property("ZCASH_RELEASE_PACKAGE_NAME").toString()

val testnetNetworkName = "Testnet"

android {
    namespace = "co.electriccoin.zcash.app"

    defaultConfig {
        applicationId = packageName

        // If Google Play deployment is triggered, then these are placeholders which are overwritten
        // when the deployment runs
        versionCode = project.property("ZCASH_VERSION_CODE").toString().toInt()
        versionName = project.property("ZCASH_VERSION_NAME").toString()

        if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
            testInstrumentationRunnerArguments["clearPackageData"] = "true"
        }

        testInstrumentationRunner = "co.electriccoin.zcash.test.ZcashUiTestRunner"
    }

    if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
        testOptions {
            execution = "ANDROIDX_TEST_ORCHESTRATOR"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += listOf(NetworkDimension.DIMENSION_NAME, DistributionDimension.DIMENSION_NAME)

    productFlavors {
        create(NetworkDimension.TESTNET.value) {
            dimension = NetworkDimension.DIMENSION_NAME
            applicationId = packageName
            applicationIdSuffix = ".testnet"
            matchingFallbacks.addAll(listOf(NetworkDimension.TESTNET.value, BuildType.DEBUG.value))

            // App ID pre Testnet (Debug projekt)
            firebaseAppDistribution {
                appId = "1:72075012504:android:2707ebbc1c0074e74ba3ed"
            }
        }

        create(NetworkDimension.MAINNET.value) {
            dimension = NetworkDimension.DIMENSION_NAME
            applicationId = packageName
            matchingFallbacks.addAll(listOf(NetworkDimension.MAINNET.value, BuildType.RELEASE.value))

            // App ID pre Mainnet (Debug projekt)
            firebaseAppDistribution {
                appId = "1:72075012504:android:235e485f5bd5fc0d4ba3ed"
            }
        }

        create(DistributionDimension.STORE.value) {
            dimension = DistributionDimension.DIMENSION_NAME
            applicationId = packageName
            matchingFallbacks.addAll(listOf(DistributionDimension.STORE.value, BuildType.RELEASE.value))
        }

        create(DistributionDimension.FOSS.value) {
            dimension = DistributionDimension.DIMENSION_NAME
            applicationId = packageName
            matchingFallbacks.addAll(listOf(DistributionDimension.FOSS.value, BuildType.RELEASE.value))
            versionNameSuffix = "-foss"
            applicationIdSuffix = ".foss"

            // FOSS projekt (všetky varianty)
            firebaseAppDistribution {
                appId = "1:228274394465:android:d88850a8c32907477a2120"
            }
        }
    }

    val releaseKeystorePath = project.property("ZCASH_RELEASE_KEYSTORE_PATH").toString()
    val releaseKeystorePassword = project.property("ZCASH_RELEASE_KEYSTORE_PASSWORD").toString()
    val releaseKeyAlias = project.property("ZCASH_RELEASE_KEY_ALIAS").toString()
    val releaseKeyAliasPassword =
        project.property("ZCASH_RELEASE_KEY_ALIAS_PASSWORD").toString()
    val isReleaseSigningConfigured = listOf(
        releaseKeystorePath,
        releaseKeystorePassword,
        releaseKeyAlias,
        releaseKeyAliasPassword
    ).all { it.isNotBlank() }

    signingConfigs {
        if (isReleaseSigningConfigured) {
            // If this block doesn't execute, the output will be unsigned
            create(BuildType.RELEASE.value).apply {
                storeFile = File(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyAliasPassword
            }
        }
    }

    buildTypes {
        getByName(BuildType.DEBUG.value).apply {
            // Note that the build-conventions defines the res configs
            isPseudoLocalesEnabled = true

            // Suffixing app package name and version to avoid collisions with other installed Zashi
            // apps (e.g. from Google Play)
            versionNameSuffix = "-debug"
            applicationIdSuffix = ".debug"
        }
        getByName(BuildType.RELEASE.value).apply {
            isMinifyEnabled = project.property("IS_MINIFY_ENABLED").toString().toBoolean()
            isShrinkResources = project.property("IS_MINIFY_ENABLED").toString().toBoolean()
            ndk.debugSymbolLevel = project.property("NDK_DEBUG_SYMBOL_LEVEL").toString()

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-project.txt"
            )

            val isReleaseBuildDebuggable = project.property("IS_RELEASE_BUILD_DEBUGGABLE")
                .toString().toBoolean()
            isDebuggable = isReleaseBuildDebuggable

            val isSignReleaseBuildWithDebugKey = project.property("IS_SIGN_RELEASE_BUILD_WITH_DEBUG_KEY")
                .toString().toBoolean()
            if (isReleaseSigningConfigured) {
                signingConfig = signingConfigs.getByName(BuildType.RELEASE.value)
            } else if (isSignReleaseBuildWithDebugKey) {
                // Warning: in this case is the release build signed with the debug key
                signingConfig = signingConfigs.getByName(BuildType.DEBUG.value)
            }

            // Prepíšeme App ID pre STORE Release varianty na Release projekt
            // Poznámka: FOSS Release ostáva vo FOSS projekte, lebo appId je definované vo flavori FOSS
            firebaseAppDistribution {
                appId = "1:1004234202961:android:b6826581ab87b8020bbd70"
            }
        }
    }

    firebaseAppDistribution {
        artifactType = "APK"
        val credentialsPath = project.findProperty("ZCASH_FIREBASE_CREDENTIALS_PATH")?.toString() ?: ""
        if (credentialsPath.isNotEmpty()) {
            serviceCredentialsFile = credentialsPath
        }
        groups = project.findProperty("ZCASH_FIREBASE_GROUPS")?.toString() ?: "EC, Zodl"
        releaseNotes = project.findProperty("ZCASH_RELEASE_NOTES")?.toString() ?: "Manual multi-variant build"
    }

    // Master task pre "one-click" deploy všetkých dôležitých verzií
    tasks.register("deployAllVariants") {
        group = "publishing"
        description = "Builds and uploads all major variants (Store Release/Debug, Testnet, FOSS)"

        dependsOn(
            // "appDistributionUploadZcashmainnetStoreRelease",
            // "appDistributionUploadZcashtestnetStoreRelease",
            "appDistributionUploadZcashmainnetStoreDebug",
            "appDistributionUploadZcashtestnetStoreDebug",
            // "appDistributionUploadZcashmainnetFossDebug",
            // "appDistributionUploadZcashtestnetFossDebug"
        )
    }

    // Zabezpečíme, aby každý upload task automaticky spustil assemble pre daný variant
    tasks.matching { it.name.startsWith("appDistributionUpload") }.all {
        val variantName = name.removePrefix("appDistributionUpload")
        dependsOn("assemble$variantName")
    }

    // Resolve final app name
    applicationVariants.all {
        val defaultAppName = project.property("ZCASH_RELEASE_APP_NAME").toString()
        val debugAppNameSuffix = project.property("ZCASH_DEBUG_APP_NAME_SUFFIX").toString()
        val fossAppNameSuffix = project.property("ZCASH_FOSS_APP_NAME_SUFFIX").toString()
        when (this.name) {
            "zcashtestnetStoreDebug" -> {
                resValue("string", "app_name", "$defaultAppName $debugAppNameSuffix $testnetNetworkName")
            }
            "zcashmainnetStoreDebug" -> {
                resValue("string", "app_name", "$defaultAppName $debugAppNameSuffix")
            }
            "zcashtestnetStoreRelease" -> {
                resValue("string", "app_name", "$defaultAppName $testnetNetworkName")
            }
            "zcashmainnetStoreRelease" -> {
                resValue("string", "app_name", defaultAppName)
            }
            "zcashtestnetFossDebug" -> {
                resValue(
                    "string",
                    "app_name",
                    "$defaultAppName $fossAppNameSuffix $debugAppNameSuffix $testnetNetworkName"
                )
            }
            "zcashmainnetFossDebug" -> {
                resValue("string", "app_name", "$defaultAppName $fossAppNameSuffix $debugAppNameSuffix")
            }
            "zcashtestnetFossRelease" -> {
                resValue("string", "app_name", "$defaultAppName $fossAppNameSuffix $testnetNetworkName")
            }
            "zcashmainnetFossRelease" -> {
                resValue("string", "app_name", defaultAppName)
            }
        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles
        includeInBundle = false
    }

    testCoverage {
        jacocoVersion = project.property("JACOCO_VERSION").toString()
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core)
    // just to support baseline profile installation needed by benchmark tests
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.zcash.sdk) // just to configure logging
    implementation(projects.crashAndroidLib)
    implementation(projects.preferenceApiLib)
    implementation(projects.preferenceImplAndroidLib)
    implementation(projects.spackleAndroidLib)
    implementation(projects.uiLib)

    androidTestImplementation(projects.testLib)

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

androidComponents {
    onVariants { variant ->
        for (output in variant.outputs) {
            // Configure strict mode in runtime
            variant.buildConfigFields.put(
                "IS_STRICT_MODE_CRASH_ENABLED",
                BuildConfigField(
                    type = "boolean",
                    value = project.property("IS_CRASH_ON_STRICT_MODE_VIOLATION").toString(),
                    comment = "Whether is the strict mode enabled"
                )
            )

            variant.resValues.put(
                // Key matches the one in crash-android-lib/src/res/values/bools.xml
                variant.makeResValueKey("bool", "co_electriccoin_zcash_crash_is_firebase_enabled"),
                ResValue(value = hasFirebaseApiKeys.toString())
            )

            val defaultVersionName = project.property("ZCASH_VERSION_NAME").toString()
            output.versionName.set(defaultVersionName)
            val gitInfo = Git.newInfo(Git.HEAD, rootDir)
            output.versionCode.set(gitInfo.commitCount)
        }

        variant.packaging.resources.excludes.addAll(listOf(
            ".readme",
        ))

        // The fixed Locale.US is intended
        if (variant.name.lowercase(Locale.US).contains(BuildType.RELEASE.value)) {
            variant.packaging.resources.excludes.addAll(listOf(
                "**/*.kotlin_metadata",
                "DebugProbesKt.bin",
                "META-INF/*.kotlin_module",
                "META-INF/*.version",
                "META-INF/android.arch**",
                "META-INF/androidx**",
                "META-INF/com.android**",
                "META-INF/com.google.android.material_material.version",
                "META-INF/com.google.dagger_dagger.version",
                "build-data.properties",
                "core.properties",
                "firebase-**.properties",
                "kotlin-tooling-metadata.json",
                "kotlin/**",
                "play-services-**.properties",
                "protolite-well-known-types.properties",
                "transport-api.properties",
                "transport-backend-cct.properties",
                "transport-runtime.properties"
            ))
        }
    }
}

fladle {
    // Firebase Test Lab has min and max values that might differ from our project's
    // These are determined by `gcloud firebase test android models list`
    @Suppress("MagicNumber", "VariableNaming")
    val FIREBASE_TEST_LAB_MIN_SDK = 27 // Minimum for Pixel2.arm device

    @Suppress("MagicNumber", "VariableNaming")
    val FIREBASE_TEST_LAB_MAX_SDK = 33

    val minSdkVersion = run {
        val buildMinSdk = project.properties["ANDROID_MIN_SDK_VERSION"].toString().toInt()
        buildMinSdk.coerceAtLeast(FIREBASE_TEST_LAB_MIN_SDK).toString()
    }
    val targetSdkVersion = run {
        val buildTargetSdk = project.properties["ANDROID_TARGET_SDK_VERSION"].toString().toInt()
        buildTargetSdk.coerceAtMost(FIREBASE_TEST_LAB_MAX_SDK).toString()
    }

    val firebaseTestLabKeyPath = project.properties["ZCASH_FIREBASE_TEST_LAB_API_KEY_PATH"].toString()
    val firebaseProject = project.properties["ZCASH_FIREBASE_TEST_LAB_PROJECT"].toString()

    if (firebaseTestLabKeyPath.isNotEmpty()) {
        serviceAccountCredentials.set(File(firebaseTestLabKeyPath))
    } else if (firebaseProject.isNotEmpty()) {
        projectId.set(firebaseProject)
    }

    @Suppress("MagicNumber")
    flakyTestAttempts.set(1)

    configs {
        val buildDirectory = layout.buildDirectory.get().asFile
        create("sanityConfigDebug") {
            clearPropertiesForSanityRobo()

            debugApk.set(
                project.provider {
                    "${buildDirectory}/outputs/apk/zcashmainnetStore/debug/app-zcashmainnet-store-debug.apk"
                }
            )

            testTimeout.set("3m")

            devices.addAll(
                mapOf("model" to "Pixel2.arm", "version" to minSdkVersion),
                mapOf("model" to "Pixel2.arm", "version" to targetSdkVersion)
            )

            flankVersion.set(libs.versions.flank.get())
        }
        create("sanityConfigRelease") {
            clearPropertiesForSanityRobo()

            debugApk.set(
                project.provider {
                    "$buildDirectory" +
                        "/outputs/apk_from_bundle/zcashmainnetStoreRelease/app-zcashmainnet-store-release-universal.apk"
                }
            )

            testTimeout.set("3m")

            devices.addAll(
                mapOf("model" to "Pixel2.arm", "version" to minSdkVersion),
                mapOf("model" to "Pixel2.arm", "version" to targetSdkVersion)
            )

            flankVersion.set(libs.versions.flank.get())
        }
    }
}
