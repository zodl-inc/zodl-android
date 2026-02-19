buildscript {
    dependencyLocking {
        // This property is treated specially, as it is not defined by default in the root gradle.properties
        // and declaring it in the root gradle.properties is ignored by included builds. This only picks up
        // a value declared as a system property, a command line argument, or a an environment variable.
        val isDependencyLockingEnabled = if (project.hasProperty("ZCASH_IS_DEPENDENCY_LOCKING_ENABLED")) {
            project.property("ZCASH_IS_DEPENDENCY_LOCKING_ENABLED").toString().toBoolean()
        } else {
            true
        }

        if (isDependencyLockingEnabled) {
            lockAllConfigurations()
        }
    }

    repositories {
        val isRepoRestrictionEnabled = true

        val googleGroups = listOf<String>(
            "com.google.firebase",
            "com.google.gms",
            "com.google.android.gms"
        )

        google {
            if (isRepoRestrictionEnabled) {
                content {
                    googleGroups.forEach { includeGroup(it) }
                }
            }
        }
        mavenCentral {
            if (isRepoRestrictionEnabled) {
                content {
                    googleGroups.forEach { excludeGroup(it) }
                }
            }
        }
        gradlePluginPortal {
            if (isRepoRestrictionEnabled) {
                content {
                    googleGroups.forEach { excludeGroup(it) }
                }
            }
        }
        maven("https://jitpack.io")
    }

    dependencies {
        val crashlyticsVersion = project.property("FIREBASE_CRASHLYTICS_BUILD_TOOLS_VERSION")
        classpath("com.google.firebase:firebase-crashlytics-gradle:$crashlyticsVersion")
        classpath("com.google.gms:google-services:${project.property("GOOGLE_PLAY_SERVICES_GRADLE_PLUGIN_VERSION")}")
    }
}

plugins {
    id("com.github.ben-manes.versions")
    id("com.osacky.fulladle")
    id("secant.detekt-conventions")
    id("secant.ktlint-conventions")
    id("secant.rosetta-conventions")
}

tasks {
    withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
        gradleReleaseChannel = "current"

        resolutionStrategy {
            componentSelection {
                all {
                    if (isNonStable(candidate.version) && !isNonStable(currentVersion)) {
                        reject("Unstable")
                    }
                }
            }
        }
    }

    register("checkProperties") {
        // Ensure that developers do not change default values of certain properties directly
        // in the repo, but instead set them in their local ~/.gradle/gradle.properties file
        // (or use command line arguments)
        val expectedPropertyValues = mapOf(
            "ZCASH_IS_TREAT_WARNINGS_AS_ERRORS" to "true",
            "IS_KOTLIN_TEST_COVERAGE_ENABLED" to "true",
            "IS_ANDROID_INSTRUMENTATION_TEST_COVERAGE_ENABLED" to "false",
            "IS_USE_TEST_ORCHESTRATOR" to "false",
            "IS_CRASH_ON_STRICT_MODE_VIOLATION" to "false",

            "ZCASH_FIREBASE_TEST_LAB_API_KEY_PATH" to "",
            "ZCASH_FIREBASE_TEST_LAB_PROJECT" to "",

            "ZCASH_EMULATOR_WTF_API_KEY" to "",

            "IS_MINIFY_ENABLED" to "true",
            "NDK_DEBUG_SYMBOL_LEVEL" to "symbol_table",

            "ZCASH_RELEASE_APP_NAME" to "Zodl",
            "ZCASH_RELEASE_PACKAGE_NAME" to "co.electriccoin.zcash",
            "IS_SECURE_SCREEN_PROTECTION_ACTIVE" to "true",
            "IS_SCREEN_ROTATION_ENABLED" to "false",

            "ZCASH_DEBUG_KEYSTORE_PATH" to "",
            "ZCASH_RELEASE_KEYSTORE_PATH" to "",
            "ZCASH_RELEASE_KEYSTORE_PASSWORD" to "",
            "ZCASH_RELEASE_KEY_ALIAS" to "",
            "ZCASH_RELEASE_KEY_ALIAS_PASSWORD" to "",

            "IS_SIGN_RELEASE_BUILD_WITH_DEBUG_KEY" to "false",
            "IS_RELEASE_BUILD_DEBUGGABLE" to "false",

            "ZCASH_GOOGLE_PLAY_SERVICE_ACCOUNT" to "",
            "ZCASH_GOOGLE_PLAY_SERVICE_ACCOUNT_KEY" to "",
            "ZCASH_GOOGLE_PLAY_PUBLISHER_API_KEY" to "",
            "ZCASH_GOOGLE_PLAY_SERVICE_KEY_FILE_PATH" to "",
            "ZCASH_GOOGLE_PLAY_DEPLOY_TRACK" to "internal",
            "ZCASH_GOOGLE_PLAY_DEPLOY_STATUS" to "draft",

            "ZCASH_FLEXA_KEY" to "",
            "ZCASH_CMC_KEY" to "",
            "SDK_INCLUDED_BUILD_PATH" to "",
            "BIP_39_INCLUDED_BUILD_PATH" to ""
        )

        val actualPropertyValues = project.properties.filterKeys { it in expectedPropertyValues.keys }

        doLast {
            val warnings = expectedPropertyValues.filter { (key, value) ->
                actualPropertyValues[key].toString() != value
            }.map { "Property ${it.key} does not have expected value \"${it.value}\"" }

            if (warnings.isNotEmpty()) {
                throw GradleException(warnings.joinToString(separator = "\n"))
            }
        }
    }
}

val unstableKeywords = listOf("alpha", "beta", "rc", "m", "ea", "build")

fun isNonStable(version: String): Boolean {
    val versionLowerCase = version.lowercase()

    return unstableKeywords.any { versionLowerCase.contains(it) }
}

fladle {
    // Firebase Test Lab has min and max values that might differ from our project's
    // These are determined by `gcloud firebase test android models list`
    @Suppress("MagicNumber", "VariableNaming")
    val FIREBASE_TEST_LAB_MIN_SDK = 27 // Minimum for Pixel2.arm device

    @Suppress("MagicNumber", "VariableNaming")
    val FIREBASE_TEST_LAB_MAX_SDK = 33

    val minSdkVersion = run {
        // Fladle will use the app APK as the additional APK, so we have to
        // use the app's minSdkVersion here.
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

    devices.addAll(
        mapOf("model" to "Pixel2.arm", "version" to minSdkVersion),
        mapOf("model" to "Pixel2.arm", "version" to targetSdkVersion)
    )

    @Suppress("MagicNumber")
    flakyTestAttempts.set(2)

    // Always use orchestrator for Firebase Test Lab.
    // Some submodules don't need it, but it is difficult to configure on a per-module basis from here
    // since this configuration applies to all modules.
    useOrchestrator.set(true)
    environmentVariables.set(mapOf("clearPackageData" to "true"))

    flankVersion.set(libs.versions.flank.get())

    filesToDownload.set(
        listOf(
            ".*/matrix_.*/.*test_results_merged\\.xml",
            ".*/matrix_.*/.*/artifacts/sdcard/googletest/test_outputfiles/.*\\.png"
        )
    )

    directoriesToPull.set(
        listOf(
            "/sdcard/googletest/test_outputfiles"
        )
    )
}
