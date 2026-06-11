plugins {
    alias(libs.plugins.nexchat.android.library)
    alias(libs.plugins.nexchat.android.hilt)
}

android {
    namespace = "com.nexchat.core.storage"
}

dependencies {
    implementation(projects.core.common)
    
    // Coroutines
    implementation(libs.coroutines.android)
    
    // Logging
    implementation(libs.timber)
    
    // Testing
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
