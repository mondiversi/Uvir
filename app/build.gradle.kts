import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseSigningDirectory =
    providers.environmentVariable("MONDIVERSI_SIGNING_DIR").orNull
        ?.takeIf { it.isNotBlank() }
        ?.let {
            File(it).also { path ->
                require(path.isAbsolute) { "MONDIVERSI_SIGNING_DIR must be an absolute path" }
            }
        }
        ?: File(System.getProperty("user.home"), "Dropbox/Mondiversi/UvirSigning")

val releaseSigningSources = listOf(
    File(releaseSigningDirectory, "signing.properties"),
    rootProject.file("keystore.properties")
).filter { it.isFile }
val releaseSigningConfigured = releaseSigningSources.isNotEmpty()

val releaseKeystoreProperties =
    Properties().apply {
        releaseSigningSources.forEach { source -> source.inputStream().use(::load) }
    }

if (releaseSigningConfigured) {
    listOf("storePassword", "keyAlias", "keyPassword").forEach { name ->
        require(!releaseKeystoreProperties.getProperty(name).isNullOrEmpty()) {
            "Private signing configuration is missing $name"
        }
    }
}

// The key stays outside the repository; do not bind another PC to a username.
val releaseKeystoreFile =
    releaseKeystoreProperties.getProperty("storeFile")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let {
            File(it).also { path -> require(path.isAbsolute) { "storeFile must be an absolute path" } }
        }
        ?: File(releaseSigningDirectory, "uvir-release.jks")

android {
    namespace = "me.mondiversi.uvir"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "me.mondiversi.uvir"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "1.4.1"
        buildConfigField(
            "String",
            "GITHUB_REPOSITORY_URL",
            "\"https://github.com/mondiversi/Uvir\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = releaseKeystoreFile
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
            // Disposable package for device tests; never migrate the installed user's archive.
            if (providers.gradleProperty("uvirIsolatedVerification").orNull == "true") {
                applicationIdSuffix = ".verification"
            }
            // Keep Android Studio Run/Debug compatible with the locally
            // installed release APK. A new PC can read the private Dropbox
            // configuration without needing a project-local credentials file.
            if (releaseSigningConfigured) {
                signingConfig =
                    signingConfigs.getByName("release")
            }
        }

        release {
            if (releaseSigningConfigured) {
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
    implementation("net.lingala.zip4j:zip4j:2.11.6")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
