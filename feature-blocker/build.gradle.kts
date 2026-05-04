plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.invigilator.feature.blocker"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // ── Module dependencies (feature modules depend only on :core) ────────────
    implementation(project(":core"))

    // ── AndroidX ──────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)

    // ── Hilt ──────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ── Firebase (Timestamp used in session doc building) ─────────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Logging ───────────────────────────────────────────────────────────────
    implementation(libs.timber)

    // ── Testing ───────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
