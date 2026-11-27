import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Flexa core 1.1.3 built from source (see ../README.md). Dependencies are those of the published
// co.flexa:core:1.1.3 runtime variant.
plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-parcelize")
    id("kotlin-kapt")
}

val flexaVersion = project.property("FLEXA_VERSION").toString()

android {
    namespace = "com.flexa"
    compileSdk = project.property("ANDROID_COMPILE_SDK_VERSION").toString().toInt()

    defaultConfig {
        minSdk = project.property("ANDROID_MIN_SDK_VERSION").toString().toInt()
        consumerProguardFiles("r8-rules.pro", "r8-gson.pro", "identity-r8-rules.pro", "kotlin-serializer-rules.pro")
        // The published artifact sets DEBUG_API_HOST to null in release; the API host is always api.flexa.co.
        buildConfigField("String", "DEBUG_API_HOST", "null")
        buildConfigField("String", "SPEND_SDK_VERSION", "\"$flexaVersion\"")
        // Upstream stamps the build date, which would make the build irreproducible.
        buildConfigField("String", "RELEASE_DATE", "\"2026-04-17\"")
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

    testOptions {
        unitTests.isReturnDefaultValues = true
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
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.ui:ui:1.10.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.6")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.runtime:runtime-livedata:1.10.6")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("joda-time:joda-time:2.12.7")
    api("com.squareup.okhttp3:okhttp-android:5.1.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.1.0")
    implementation("com.squareup.okhttp3:okhttp-sse:5.1.0")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("com.google.crypto.tink:tink-android:1.18.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("androidx.room:room-runtime:2.7.2")
    kapt("androidx.room:room-compiler:2.7.2")
    // Room 2.7's kotlin-metadata-jvm cannot read the metadata of the app's Kotlin version.
    kapt("org.jetbrains.kotlin:kotlin-metadata-jvm:${project.property("KOTLIN_VERSION")}")

    testImplementation("junit:junit:4.13.2")
}
