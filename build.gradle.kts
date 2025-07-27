// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false

    id("com.android.library") version "8.1.2" apply false


}

buildscript {
    repositories {
        google()
        mavenCentral()

    }
    dependencies {
        classpath("com.google.gms:google-services:4.4.0")  // Latest version (update if needed)
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.21") // Update latest Kotlin version
    }
}


