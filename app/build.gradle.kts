plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.bsw.plakatradar"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.bsw.plakatradar"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.10.0-location-dependency-fix"
    }

    buildFeatures {
        compose = true
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    implementation("com.google.zxing:core:3.5.4")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
