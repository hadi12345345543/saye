plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ir.saya.lan"
    compileSdk = 35
    defaultConfig {
        applicationId = "ir.saya.lan"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
}
