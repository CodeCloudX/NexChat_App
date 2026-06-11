plugins {
    alias(libs.plugins.nexchat.android.compose)
    alias(libs.plugins.nexchat.android.library)
    alias(libs.plugins.nexchat.android.hilt)
}

android {
    namespace = "com.nexchat.feature.auth"
    buildFeatures { buildConfig = true }
    defaultConfig {
        val properties = java.util.Properties()
        val localProperties = project.rootProject.file("local.properties")
        if (localProperties.exists()) {
            properties.load(localProperties.inputStream())
        }
        val googleWebClientId = properties.getProperty("google.web.client.id") ?: ""
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.auth)
    implementation(projects.core.network)

    // Retrofit Response<T> types used in AuthRepository
    implementation(libs.retrofit)

    // Material Icons (not in the compose bundle by default)
    implementation("androidx.compose.material:material-icons-core")

    // Firebase Auth + Phone Auth
    // Firebase BOM 33.12.0 ships firebase-auth 23.2.0 (Kotlin 2.1.x compatible).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    // Google Sign-In via CredentialManager
    implementation(libs.credential.manager)
    implementation(libs.credential.manager.play)
    implementation(libs.googleid)

    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation)

    // Coroutines
    implementation(libs.coroutines.android)

    // Compose UI tests (LoginScreenTest)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.mockk.android)
}
