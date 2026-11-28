import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ru.konem.ebis"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.konem.planner"
        minSdk = 26
        targetSdk = 35
        // Версия из тега релиза: ./gradlew assembleRelease -PappVersion=2.1.0 → versionCode 20100.
        val appVersion = ((project.findProperty("appVersion") as String?) ?: "2.4.1").removePrefix("v")
        val parts = appVersion.substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }
        versionCode = if (parts.size == 3) parts[0] * 10000 + parts[1] * 100 + parts[2] else 1
        versionName = appVersion
    }

    // Ключ подписи в репозитории не лежит. Локально — keystore.properties в корне (в .gitignore),
    // в GitHub Actions — переменные EBIS_KEYSTORE* из секретов. Без них сборка подписывается отладочным ключом:
    // форк соберётся, но поверх официального APK такая сборка не встанет.
    val signProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { this.load(it) }
    }
    fun sign(prop: String, env: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: signProps.getProperty(prop)
    val storePath = sign("storeFile", "EBIS_KEYSTORE")
    signingConfigs {
        if (storePath != null) create("ebis") {
            storeFile = rootProject.file(storePath)
            storePassword = sign("storePassword", "EBIS_KEYSTORE_PASSWORD")
            keyAlias = sign("keyAlias", "EBIS_KEY_ALIAS")
            keyPassword = sign("keyPassword", "EBIS_KEY_PASSWORD")
        }
    }
    val releaseSigning = signingConfigs.findByName("ebis") ?: signingConfigs.getByName("debug")

    buildTypes {
        debug {
            signingConfig = releaseSigning
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":shared"))
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    testImplementation("junit:junit:4.13.2")
}
