plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "gr.edusuite.invoicer"
    compileSdk = 37

    defaultConfig {
        applicationId = "gr.edusuite.invoicer"
        minSdk = 26
        targetSdk = 37
        // Above the last v0.x build (53) so v2 installs over old development builds.
        versionCode = 209
        versionName = "2.4.0"
        // Runs instrumented tests against a throwaway database (app/src/androidTest/.../InvoicerTestRunner.kt).
        testInstrumentationRunner = "gr.edusuite.invoicer.InvoicerTestRunner"
    }

    // CI signs the release build with the owner's permanent key on Rebuild_v2 only (D23,
    // docs/SIGNING_BRIEF.md): it decodes the keystore to the path in INVOICER_RELEASE_KEYSTORE and passes
    // its password in INVOICER_KEYSTORE_PASSWORD. Never change the key or its alias.
    val releaseKeystore = providers.environmentVariable("INVOICER_RELEASE_KEYSTORE").orNull?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                val password = providers.environmentVariable("INVOICER_KEYSTORE_PASSWORD").orNull
                require(!password.isNullOrEmpty()) { "INVOICER_RELEASE_KEYSTORE is set but INVOICER_KEYSTORE_PASSWORD is empty" }
                storeFile = file(releaseKeystore)
                storePassword = password
                keyPassword = password // PKCS12 keys share the keystore password.
                keyAlias = providers.environmentVariable("INVOICER_KEY_ALIAS").orNull?.takeIf { it.isNotBlank() } ?: "release"
            }
        }
    }

    buildTypes {
        // A separate app, "Course Invoicer (debug)", signed with the stock debug key, so a debug build
        // never installs over the tutor's real app or touches its data (D23).
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        // The build GitHub releases ship (D16): minified, 1.9 MB instead of 12.5 MB, and smoother
        // than a debuggable build. Not a Play Store build (D9). Without INVOICER_RELEASE_KEYSTORE
        // (Android Studio, local builds, other branches) the debug key signs it, so it still installs,
        // but it cannot install over a release.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
