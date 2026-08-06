plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.stan2032.gravitygambit"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.stan2032.gravitygambit"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "0.10.1"
    }

    signingConfigs {
        // Release signing comes from the environment (local keystore or CI secrets);
        // see docs/RELEASE.md. Falls back to the debug key so release builds are
        // always installable for testing.
        val ksFile = System.getenv("GG_KEYSTORE_FILE")
        if (ksFile != null) {
            create("release") {
                storeFile = file(ksFile)
                storePassword = System.getenv("GG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GG_KEY_ALIAS")
                keyPassword = System.getenv("GG_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 + resource shrinking (performance directive). Keep rules for
            // kotlinx-serialization live in proguard-rules.pro; release QA must
            // exercise save/load + billing paths (docs/RELEASE.md).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric smoke tests need real resources; this is the closest
            // we get to launching the app without a device (docs/TESTING.md).
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.billing.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
