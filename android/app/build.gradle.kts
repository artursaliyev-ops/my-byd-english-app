plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val transcriptAssets = layout.buildDirectory.dir("generated/transcriptAssets")

android {
    namespace = "app.bydplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.bydplayer"
        // From "Find out DiLink capabilities": DiLink on the Song Plus is Android 9-13.
        minSdk = 28
        targetSdk = 33
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // Whisper transcripts from the repo's transcripts/ go into the APK as assets/transcripts/<id>.json.
    sourceSets["main"].assets.srcDir(transcriptAssets)
}

val copyTranscripts by tasks.registering(Sync::class) {
    from(rootProject.file("../transcripts")) { include("*.json") }
    into(transcriptAssets.map { it.dir("transcripts") })
}
tasks.named("preBuild") { dependsOn(copyTranscripts) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    testImplementation(libs.junit)
}
