import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Flexa spend 1.1.3 built from source (see ../README.md). Dependencies are those of the published
// co.flexa:spend:1.1.3 runtime variant, with core taken from :flexa-core.
plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.flexa.spend"
    compileSdk = project.property("ANDROID_COMPILE_SDK_VERSION").toString().toInt()

    defaultConfig {
        minSdk = project.property("ANDROID_MIN_SDK_VERSION").toString().toInt()
        consumerProguardFiles("r8-rules.pro", "kotlin-serializer-rules.pro", "joda-rules.pro")
        buildConfigField("String", "SPEND_VERSION", "\"${project.property("FLEXA_VERSION")}\"")
    }

    compileOptions {
        val javaVersion = JavaVersion.toVersion(project.property("ANDROID_JVM_TARGET").toString())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Android lint's K2 UAST crashes on this upstream code (upstream builds with android.lint.useK2Uast=false);
// it is third-party code, so it is not linted here.
tasks.configureEach {
    if (name.startsWith("lint")) enabled = false
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(project.property("ANDROID_JVM_TARGET").toString()))
    }
}

dependencies {
    api(projects.flexaCore)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.ui:ui:1.10.6")
    implementation("androidx.compose.ui:ui-util:1.10.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.6")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.compose.runtime:runtime-livedata:1.10.6")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("com.google.zxing:core:3.5.3")
    implementation("commons-codec:commons-codec:1.15")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("com.google.crypto.tink:tink-android:1.18.0")
}
