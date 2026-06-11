plugins {
    alias(libs.plugins.nexchat.android.library)
    alias(libs.plugins.nexchat.android.hilt)
}

android {
    namespace = "com.nexchat.core.media"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.storage)
    
    // Compressor
    implementation(libs.compressor)
    
    // Blurhash
    implementation(libs.blurhash)
    
    // Coroutines
    implementation(libs.coroutines.android)
    
    // Network (OkHttp for raw downloader, Retrofit for MediaApi Response type)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    
    // Logging
    implementation(libs.timber)
    
    // Testing
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
