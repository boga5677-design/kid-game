plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val stableKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val stableKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val stableKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val stableKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")

val hasStableSigning =
    !stableKeystorePath.isNullOrBlank() &&
    !stableKeystorePassword.isNullOrBlank() &&
    !stableKeyAlias.isNullOrBlank() &&
    !stableKeyPassword.isNullOrBlank() &&
    stableKeystorePath?.let { file(it).exists() } == true

android {
    namespace = "com.boga.kidgame"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.boga.kidgame"
        minSdk = 24
        targetSdk = 35
        versionCode = 36
        versionName = "0.8.3"
    }

    val stableSigningConfig = if (hasStableSigning) {
        signingConfigs.create("stable") {
            storeFile = file(requireNotNull(stableKeystorePath))
            storePassword = stableKeystorePassword
            keyAlias = stableKeyAlias
            keyPassword = stableKeyPassword
        }
    } else {
        null
    }

    buildTypes {
        getByName("debug") {
            // GitHub Actions 有設定固定簽名 Secrets 時，debug APK 也使用同一把固定金鑰。
            // 之後可直接覆蓋安裝，不會每次因 Actions 產生不同 debug key 而要求刪除 App。
            if (stableSigningConfig != null) {
                signingConfig = stableSigningConfig
            }
        }

        getByName("release") {
            isMinifyEnabled = false
            if (stableSigningConfig != null) {
                signingConfig = stableSigningConfig
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
