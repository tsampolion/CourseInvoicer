// AGP 9 ships built-in Kotlin with an older Kotlin Gradle plugin. Putting KGP on the root classpath
// pins the Kotlin version for every module (AGP picks the higher version) and lets :core apply
// org.jetbrains.kotlin.jvm without repeating the version.
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
