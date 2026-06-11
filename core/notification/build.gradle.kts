plugins {
    alias(libs.plugins.nexchat.android.library)
    alias(libs.plugins.nexchat.android.hilt)
}

android {
    namespace = "com.nexchat.core.notification"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.auth)
    implementation(projects.core.work)
    
    // Coroutines
    implementation(libs.coroutines.android)
    
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    
    // WorkManager (for dispatching sync jobs from FCM)
    implementation(libs.work.runtime.ktx)
    
    // Logging
    implementation(libs.timber)
    
    // Testing
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
