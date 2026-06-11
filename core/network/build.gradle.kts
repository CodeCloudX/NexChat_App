plugins {
    alias(libs.plugins.nexchat.android.library)
    alias(libs.plugins.nexchat.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.nexchat.core.network"
    
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "API_BASE_URL", "\"https://codecloudex.dpdns.org/api/v1/\"")
        buildConfigField("String", "WS_BASE_URL", "\"wss://codecloudex.dpdns.org/ws/chat\"")
        buildConfigField("String", "CERT_PIN_PRIMARY", "\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=\"")
        buildConfigField("String", "CERT_PIN_BACKUP", "\"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=\"")
    }
}

dependencies {
    implementation(projects.core.auth)
    implementation(projects.core.common)

    implementation(libs.bundles.okhttp)
    implementation(libs.retrofit)
    implementation(libs.bundles.serialization)
    
    implementation(libs.timber)
    implementation(libs.lifecycle.process)
    
    // Testing
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
