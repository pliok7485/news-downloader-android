// v1.1 — 簽章金鑰改由 GitHub Secrets 提供（金鑰本體不再進 repo）
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 簽章金鑰「不放在 repo 裡」。
// - CI：由 GitHub Secrets 還原 android/app/news-release.jks，並用環境變數傳密碼
// - 本機：把 .jks 放到 android/app/ 並設 NEWS_KEYSTORE_PASSWORD 環境變數即可簽章
// 沒有金鑰時仍可 assembleRelease，只是產出 unsigned APK（僅供測試）
val keystoreFile = file("news-release.jks")
val keystorePassword: String? = System.getenv("NEWS_KEYSTORE_PASSWORD")
val canSign = keystoreFile.exists() && !keystorePassword.isNullOrEmpty()

android {
    namespace = "com.pliok.newsdownloader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pliok.newsdownloader"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword ?: ""
                keyAlias = System.getenv("NEWS_KEY_ALIAS") ?: "news"
                keyPassword = System.getenv("NEWS_KEY_PASSWORD") ?: (keystorePassword ?: "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (canSign) {
                signingConfig = signingConfigs.getByName("release")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // 抓 HTML 與解析內文（取代 Python 版 bs4 / trafilatura）
    implementation("org.jsoup:jsoup:1.17.2")
}
