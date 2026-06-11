plugins {
    alias(libs.plugins.nexchat.android.compose)
    alias(libs.plugins.nexchat.android.library)
    alias(libs.plugins.nexchat.android.hilt)
}

android {
    namespace = "com.nexchat.feature.contacts"
}

dependencies {
    implementation(projects.core.db)
    implementation(projects.core.network)
    implementation(projects.core.auth)
    implementation(projects.core.common)
    implementation(projects.feature.chat)
    implementation(projects.feature.groups)
    implementation(libs.retrofit)
    implementation(projects.design)
    implementation(libs.hilt.navigation)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.bundles.coil)
    implementation(libs.immutable.collections)
    implementation(libs.timber)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
}
