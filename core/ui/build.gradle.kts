plugins {
    alias(libs.plugins.nexchat.android.compose)
    alias(libs.plugins.nexchat.android.library)
}

android {
    namespace = "com.nexchat.core.ui"
}

dependencies {
    implementation(libs.immutable.collections)
}
