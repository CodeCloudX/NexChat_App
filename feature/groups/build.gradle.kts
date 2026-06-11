plugins {
    alias(libs.plugins.nexchat.android.compose)
    alias(libs.plugins.nexchat.android.library)
    alias(libs.plugins.nexchat.android.hilt)
}

android {
    namespace = "com.nexchat.feature.groups"
}

dependencies {
    implementation(projects.core.db)
    implementation(projects.core.network)
    implementation(projects.core.common)
    implementation(libs.retrofit)
    implementation(projects.design)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.bundles.coil)
    implementation(libs.hilt.navigation)
    implementation(libs.immutable.collections)
    implementation(libs.timber)
}
