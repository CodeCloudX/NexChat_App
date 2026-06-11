plugins {
    alias(libs.plugins.nexchat.android.library)
    alias(libs.plugins.nexchat.android.hilt)
}

android {
    namespace = "com.nexchat.core.auth"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.security.crypto)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
}
