plugins {
    alias(libs.plugins.nexchat.android.library)
    alias(libs.plugins.nexchat.android.hilt)
}

android {
    namespace = "com.nexchat.core.work"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.auth)
    implementation(projects.core.network)
    implementation(projects.core.db)
    implementation(projects.core.security)

    // Direct classpath for types used in workers
    implementation(libs.retrofit)
    implementation(libs.libsignal.client)
    
    // Coroutines
    implementation(libs.coroutines.android)
    
    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    
    // Logging
    implementation(libs.timber)
    
    // Testing
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
