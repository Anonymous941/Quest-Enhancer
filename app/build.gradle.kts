plugins {
    id("com.android.application")
}

android {
    namespace = "org.codeberg.anonymous950.questenhancer"

    compileSdk = 37
    defaultConfig {
        applicationId = "org.codeberg.anonymous950.questenhancer"
        minSdk = 27
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.2"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlin {
        jvmToolchain(8)
    }
}

dependencies {
    implementation(libs.kotlin.reflect)

    compileOnly(libs.xposed)
}
