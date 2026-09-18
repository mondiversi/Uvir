import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseKeystorePropertiesFile =
    rootProject.file("keystore.properties")

val releaseKeystoreProperties =
    Properties().apply {
        if (releaseKeystorePropertiesFile.exists()) {
            releaseKeystorePropertiesFile
                .inputStream()
                .use(::load)
        }
    }

android {
    namespace = "me.mondiversi.uvir"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "me.mondiversi.uvir"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
        buildConfigField(
            "String",
            "GITHUB_REPOSITORY_URL",
            "\"https://github.com/mondiversi/Uvir\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystorePropertiesFile.exists()) {
            create("release") {
                storeFile =
                    file(
                        releaseKeystoreProperties
                            .getProperty("storeFile")
                    )
                storePassword =
                    releaseKeystoreProperties
                        .getProperty("storePassword")
                keyAlias =
                    releaseKeystoreProperties
                        .getProperty("keyAlias")
                keyPassword =
                    releaseKeystoreProperties
                        .getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Keep Android Studio Run/Debug compatible with the locally
            // installed release APK. The private key stays outside Git and
            // this falls back to Android's debug key on other computers.
            if (releaseKeystorePropertiesFile.exists()) {
                signingConfig =
                    signingConfigs.getByName("release")
            }
        }

        release {
            if (releaseKeystorePropertiesFile.exists()) {
                signingConfig =
                    signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Uvir changes language inside the app, so every translation must remain
    // available even when the release is delivered as an Android App Bundle.
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(
        "com.github.mik3y:usb-serial-for-android:3.11.0"
    )
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
