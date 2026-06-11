plugins {
    alias(libs.plugins.nexchat.android.compose)
    alias(libs.plugins.nexchat.android.library)
    alias(libs.plugins.nexchat.android.hilt)
}

android {
    namespace = "com.nexchat.feature.media"
}

dependencies {
    implementation(projects.core.db)
    implementation(projects.core.network)
    implementation(projects.core.storage)
    implementation(projects.core.common)
    implementation(projects.design)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.bundles.camerax)
    implementation(libs.bundles.coil)
    implementation(libs.telephoto.zoomable.coil)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.immutable.collections)
    implementation(libs.hilt.navigation)
    implementation(libs.timber)
}
