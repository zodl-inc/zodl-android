import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ManagedVirtualDevice
import model.BuildType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

pluginManager.withPlugin("com.android.application") {
    project.the<com.android.build.gradle.AppExtension>().apply {
        configureBaseExtension()

        defaultConfig {
            minSdk = project.property("ANDROID_MIN_SDK_VERSION").toString().toInt()
            targetSdk = project.property("ANDROID_TARGET_SDK_VERSION").toString().toInt()

            // en_XA and ar_XB are pseudolocales for debugging.
            // The rest of the locales provides an explicit list of the languages to keep in the
            // final app.  Doing this will strip out additional locales from libraries like
            // Google Play Services and Firebase, which add unnecessary bloat.
            resourceConfigurations.addAll(listOf("en", "en-rUS", "en-rGB", "en-rAU", "es", "pt", "en_XA", "ar_XB"))

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            testInstrumentationRunnerArguments["useTestStorageService"] = "true"
            if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
                testInstrumentationRunnerArguments["clearPackageData"] = "true"
            }
        }
    }
}

pluginManager.withPlugin("com.android.library") {
    project.the<com.android.build.gradle.LibraryExtension>().apply {
        configureBaseExtension()

        defaultConfig {
            minSdk = project.property("ANDROID_MIN_SDK_VERSION").toString().toInt()
            // This is deprecated but we don't have a replacement for the instrumentation APKs yet
            // TODO [#1815]: Gradle targetSdk deprecated #1815
            // TODO [#1815]: https://github.com/Electric-Coin-Company/zashi-android/issues/1815
            targetSdk = project.property("ANDROID_TARGET_SDK_VERSION").toString().toInt()

            // The last two are for support of pseudolocales in debug builds.
            // If we add other localizations, they should be included in this list.
            // By explicitly setting supported locales, we strip out unused localizations from third party
            // libraries (e.g. play services)
            // TODO [#1816]: Gradle resourceConfigurations deprecation #1816
            // TODO [#1816]: https://github.com/Electric-Coin-Company/zashi-android/issues/1816
            resourceConfigurations.addAll(listOf("en", "en-rUS", "en-rGB", "en-rAU", "es", "pt", "en_XA", "ar_XB"))

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            consumerProguardFiles("proguard-consumer.txt")

            testInstrumentationRunnerArguments["useTestStorageService"] = "true"
            if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
                testInstrumentationRunnerArguments["clearPackageData"] = "true"
            }
        }
        testCoverage {
            jacocoVersion = project.property("JACOCO_VERSION").toString()
        }
    }
}

pluginManager.withPlugin("com.android.test") {
    project.the<com.android.build.gradle.TestExtension>().apply {
        configureBaseExtension()

        defaultConfig {
            minSdk = project.property("ANDROID_MIN_SDK_VERSION").toString().toInt()
            targetSdk = project.property("ANDROID_TARGET_SDK_VERSION").toString().toInt()

            // The last two are for support of pseudolocales in debug builds.
            // If we add other localizations, they should be included in this list.
            // By explicitly setting supported locales, we strip out unused localizations from third party
            // libraries (e.g. play services)
            // TODO [#1816]: Gradle resourceConfigurations deprecation #1816
            // TODO [#1816]: https://github.com/Electric-Coin-Company/zashi-android/issues/1816
            resourceConfigurations.addAll(listOf("en", "en-rUS", "en-rGB", "en-rAU", "es", "pt", "en_XA", "ar_XB"))

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            testInstrumentationRunnerArguments["useTestStorageService"] = "true"
            if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
                testInstrumentationRunnerArguments["clearPackageData"] = "true"
            }
        }
        testCoverage {
            jacocoVersion = project.property("JACOCO_VERSION").toString()
        }
    }
}

@Suppress("LongMethod")
fun com.android.build.gradle.BaseExtension.configureBaseExtension() {
    compileSdkVersion(project.property("ANDROID_COMPILE_SDK_VERSION").toString().toInt())
    ndkVersion = project.property("ANDROID_NDK_VERSION").toString()

    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        val javaVersion = JavaVersion.toVersion(project.property("ANDROID_JVM_TARGET").toString())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    buildTypes {
        getByName(BuildType.DEBUG.value).apply {
            val coverageEnabled =
                project.property("IS_ANDROID_INSTRUMENTATION_TEST_COVERAGE_ENABLED").toString().toBoolean()
            isTestCoverageEnabled = coverageEnabled
            enableAndroidTestCoverage = coverageEnabled
            enableUnitTestCoverage = coverageEnabled
        }
    }

    signingConfigs {
        val debugKeystorePath = project.property("ZCASH_DEBUG_KEYSTORE_PATH").toString()
        val isExplicitDebugSigningEnabled = !debugKeystorePath.isNullOrBlank()
        if (isExplicitDebugSigningEnabled) {
            // If this block doesn't execute, the output will still be signed with the default keystore
            getByName(BuildType.DEBUG.value).apply {
                storeFile = File(debugKeystorePath)
            }
        }
    }

    testOptions {
        animationsDisabled = true

        if (project.property("IS_USE_TEST_ORCHESTRATOR").toString().toBoolean()) {
            execution = "ANDROIDX_TEST_ORCHESTRATOR"
        }

        @Suppress("UnstableApiUsage")
        managedDevices {
            @Suppress("MagicNumber", "VariableNaming")
            val MANAGED_DEVICES_MIN_SDK = 27

            val testDeviceMinSdkVersion = run {
                val buildMinSdk = project.properties["ANDROID_MIN_SDK_VERSION"].toString().toInt()
                buildMinSdk.coerceAtLeast(MANAGED_DEVICES_MIN_SDK)
            }
            val testDeviceMaxSdkVersion = project.properties["ANDROID_TARGET_SDK_VERSION"].toString().toInt()

            allDevices {
                create<ManagedVirtualDevice>("pixel2Min") {
                    device = "Pixel 2"
                    apiLevel = testDeviceMinSdkVersion
                    systemImageSource = "google"
                }
                create<ManagedVirtualDevice>("pixel2Target") {
                    device = "Pixel 2"
                    apiLevel = testDeviceMaxSdkVersion
                    systemImageSource = "google"
                }
            }

            groups {
                create("defaultDevices") {
                    targetDevices.addAll(allDevices.toList())
                }
            }
        }
    }

    packagingOptions {
        resources.excludes.addAll(
            listOf(
                "META-INF/AL2.0",
                "META-INF/ASL2.0",
                "META-INF/DEPENDENCIES",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE",
                "META-INF/LICENSE-notice.md",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/license.txt",
                "META-INF/notice.txt"
            )
        )
    }

    project.extensions.findByType<KotlinAndroidProjectExtension>()?.apply {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(project.property("ANDROID_JVM_TARGET").toString()))
            allWarningsAsErrors.set(project.property("ZCASH_IS_TREAT_WARNINGS_AS_ERRORS").toString().toBoolean())
            freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn")
            freeCompilerArgs.addAll(project.buildComposeMetricsParameters())
        }
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        project.extensions.findByType<ComposeCompilerGradlePluginExtension>()?.apply {
            stabilityConfigurationFiles.add(project.rootProject.layout.projectDirectory
                .file("compose_stability_config.conf"))
        }
    }

    dependencies {
        add(
            "coreLibraryDesugaring",
            "com.android.tools:desugar_jdk_libs:${project.property("CORE_LIBRARY_DESUGARING_VERSION")}"
        )
    }
}

fun Project.buildComposeMetricsParameters(): List<String> {
    val metricParameters = mutableListOf<String>()
    val enableMetricsProvider = project.providers.gradleProperty("IS_ENABLE_COMPOSE_COMPILER_METRICS")
    val enableMetrics = (enableMetricsProvider.orNull == "true")
    val buildDirectory = layout.buildDirectory.get().asFile
    if (enableMetrics) {
        val metricsFolder = File(buildDirectory, "compose-metrics")
        metricParameters.add("-P")
        metricParameters.add(
            "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=" + metricsFolder.absolutePath
        )
    }

    val enableReportsProvider = project.providers.gradleProperty("IS_ENABLE_COMPOSE_COMPILER_REPORTS")
    val enableReports = (enableReportsProvider.orNull == "true")
    if (enableReports) {
        val reportsFolder = File(buildDirectory, "compose-reports")
        metricParameters.add("-P")
        metricParameters.add(
            "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" + reportsFolder.absolutePath
        )
    }
    return metricParameters.toList()
}
