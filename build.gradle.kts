buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // اضافه کردن Google Services Gradle Plugin
        classpath("com.google.gms:google-services:4.3.10") // یا نسخه‌ی جدیدتر
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
