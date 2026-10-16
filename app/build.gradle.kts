plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "gr.eduinvoice"
    compileSdk = 37

    defaultConfig {
        applicationId = "gr.eduinvoice"
        minSdk = 26
        targetSdk = 36
        // Above the last v0.x build (53) so v2 installs over old development builds.
        versionCode = 202
        versionName = "2.0.0-alpha03"
        // Runs instrumented tests against a throwaway database (app/src/androidTest/.../EduTestRunner.kt).
        testInstrumentationRunner = "gr.eduinvoice.EduTestRunner"
    }

    signingConfigs {
        getByName("debug") {
            // CI signs every debug build with the owner's key (secrets SIGNING_KEYSTORE_B64 and
            // SIGNING_PASSWORD), so a new build installs over the previous one and keeps the tutor's data
            // (README, "Install a test build"). Without these variables the default debug key is used.
            providers.environmentVariable("EDUINVOICE_DEBUG_KEYSTORE").orNull?.let { path ->
                val password = providers.environmentVariable("EDUINVOICE_KEYSTORE_PASSWORD").orNull ?: "android"
                storeFile = file(path)
                storePassword = password
                keyPassword = password // PKCS12 keys share the keystore password.
                keyAlias = providers.environmentVariable("EDUINVOICE_KEY_ALIAS").orNull ?: "androiddebugkey"
            }
        }
    }

    buildTypes {
        // MARKED FOR DELETION (D9): no Play Store release. See docs/PRODUCT_SPEC.md §8 before deleting:
        // this is also the only small (R8) build.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        // ViewModel tests touch SavedStateHandle, which references android.* stubs.
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

// Approved dependencies only; see docs/PRODUCT_SPEC.md §7 (dependency register) before adding anything.
dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(testFixtures(project(":core")))
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
