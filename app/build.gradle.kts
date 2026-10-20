plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun prop(name: String): String = (findProperty(name) as String?) ?: error("Propriété manquante : $name")

android {
    namespace = "dev.wido.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.wido.tv"
        minSdk = 26
        targetSdk = 34
        // Surchargés par la CI : -Pwido.versionName=1.2.3 -Pwido.versionCode=42
        versionCode = (findProperty("wido.versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("wido.versionName") as String?) ?: "0.1.0"
        buildConfigField("String", "GITHUB_REPO", "\"${prop("wido.githubRepo")}\"")
        buildConfigField("String", "CONFIG_URL", "\"${prop("wido.configUrl")}\"")
    }

    val keystorePath = System.getenv("WIDO_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("WIDO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WIDO_KEY_ALIAS")
                keyPassword = System.getenv("WIDO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 : code et ressources inutilisés retirés, code optimisé. APK plus léger, démarrage plus rapide.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sans clé de release (build local), on signe avec la clé debug : l'APK s'installe,
            // mais ne pourra pas être mis à jour par une release signée avec une autre clé.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.webkit:webkit:1.12.1")
    // Installe les profils de démarrage (Compose, app) même hors Play Store : moins de saccades au lancement.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // Lecteur natif « Lire dans WiDo » : HLS, DASH et fichiers vidéo, décodage matériel.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // QR code de la saisie depuis un téléphone (génération seulement).
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
