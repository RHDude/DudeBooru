import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Ключ релизной подписи: из переменных окружения (сборка в GitHub Actions) или из keystore.properties
 * в корне проекта (локальная сборка; файл и сам ключ в git не попадают). Нет ключа — release собирается
 * неподписанным, подпись ставится отдельно (см. .github/workflows/release.yml).
 */
val keystoreProperties: Properties? = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

fun signingValue(env: String, property: String): String? =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() } ?: keystoreProperties?.getProperty(property)

android {
    namespace = "app.dudebooru"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.dudebooru"
        minSdk = 26
        targetSdk = 36
        // major * 10000 + minor * 100 + patch
        versionCode = 900
        versionName = "0.9.0"
        // Проверка обновлений через GitHub Releases; для F-Droid выключается: ./gradlew -PnoUpdateCheck
        buildConfigField("boolean", "UPDATE_CHECK", (!providers.gradleProperty("noUpdateCheck").isPresent).toString())
    }

    androidResources {
        // Язык приложения выбирается и в системных настройках (Android 13+): английский и русский.
        generateLocaleConfig = true
    }

    signingConfigs {
        create("release") {
            val store = signingValue("DUDEBOORU_KEYSTORE", "storeFile")
            if (store != null) {
                storeFile = rootProject.file(store)
                storePassword = signingValue("DUDEBOORU_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("DUDEBOORU_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("DUDEBOORU_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // F-Droid: без зашифрованного блока зависимостей Google в APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":booru"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.telephoto.coil3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.okhttp)
    implementation(libs.androidx.work)
    implementation(libs.okhttp.doh)
    implementation(libs.androidx.documentfile)

    testImplementation(libs.junit)
}
