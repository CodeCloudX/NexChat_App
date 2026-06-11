plugins {
    alias(libs.plugins.nexchat.android.library)
    alias(libs.plugins.nexchat.android.hilt)
}

android {
    namespace = "com.nexchat.core.security"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.db)
    implementation(projects.core.auth)
    
    // Signal Protocol for E2EE
    implementation(libs.libsignal.client)
    implementation(libs.libsignal.android)
    
    // Encryption & Crypto
    implementation(libs.security.crypto)
    implementation(libs.argon2kt)
    
    // Biometric Auth
    implementation(libs.biometric)
    
    // Serialization
    implementation(libs.bundles.serialization)
    
    // Logging
    implementation(libs.timber)
    
    // Testing
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
