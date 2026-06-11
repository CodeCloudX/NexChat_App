plugins {
    alias(libs.plugins.nexchat.android.compose)
    alias(libs.plugins.nexchat.android.library)
    alias(libs.plugins.nexchat.android.hilt)
}

android {
    namespace = "com.nexchat.feature.onboarding"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.auth)
    implementation(projects.core.network)
    implementation(projects.core.security)

    // Image loading (AsyncImage in ProfileSetupScreen)
    implementation(libs.bundles.coil)

    // Material Icons (not in the compose bundle by default)
    implementation("androidx.compose.material:material-icons-core")

    // OkHttp + Retrofit (MultipartBody avatar upload, Response types)
    implementation(libs.okhttp)
    implementation(libs.retrofit)

    // Signal key serialization
    implementation(libs.libsignal.client)

    // Image compression
    implementation(libs.compressor)

    // DataStore (E2EE shown flag)
    implementation(libs.datastore.preferences)

    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation)

    // Coroutines
    implementation(libs.coroutines.android)
}
