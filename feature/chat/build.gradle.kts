plugins {
    alias(libs.plugins.nexchat.android.compose)
    alias(libs.plugins.nexchat.android.library)
    alias(libs.plugins.nexchat.android.hilt)
}

android {
    namespace = "com.nexchat.feature.chat"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:db"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))
    implementation(project(":core:security"))
    implementation(project(":core:work"))
    implementation(project(":core:ui"))
    implementation(project(":design"))

    implementation(libs.bundles.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation)
    implementation(libs.immutable.collections)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.bundles.coil)
    implementation(libs.timber)
    implementation(libs.coroutines.core)
    implementation(libs.retrofit)
    // Material Icons Extended — required for DoneAll, AccessTime, WifiOff
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.blurhash)

    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.truth)
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.0")
    testImplementation("androidx.work:work-testing:2.10.0")
}
