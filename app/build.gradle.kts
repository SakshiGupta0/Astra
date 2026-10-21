// ASTRA app — version lives here (versionName) and is shown in Profile → About.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase is switched on by adding app/google-services.json (see docs/FIREBASE-SETUP.md).
// Without it the app still builds and runs; sign-in, backup and Ask ASTRA show as "being set up".
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) apply(plugin = "com.google.gms.google-services")
// Address of the AI server (Cloudflare Worker), written by the deploy workflow.
val aiUrl = rootProject.file("ai-endpoint.txt").takeIf { it.exists() }?.readText()?.trim() ?: ""
// Contact address printed in the privacy notice (optional; the notice falls back to "Send feedback").
val contactEmail = rootProject.file("contact-email.txt").takeIf { it.exists() }?.readText()?.trim() ?: ""

android {
    namespace = "app.astra"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.astra"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "FIREBASE", firebaseConfigured.toString())
        buildConfigField("String", "AI_URL", "\"$aiUrl\"")
        buildConfigField("String", "CONTACT_EMAIL", "\"$contactEmail\"")
    }

    // One fixed key for every build, so a new APK installs over the old one and keeps your data.
    signingConfigs {
        create("astra") {
            storeFile = rootProject.file("keystore/astra.jks")
            storePassword = "astra-build"
            keyAlias = "astra"
            keyPassword = "astra-build"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("astra") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("astra")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    // The privacy notice is shipped from the same file the AI server publishes at /privacy.
    sourceSets["main"].assets.srcDirs("src/main/assets", "../docs/privacy")
}

dependencies {
    implementation(project(":core"))

    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    androidTestImplementation(bom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Sign-in, backup and the AI gateway
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
