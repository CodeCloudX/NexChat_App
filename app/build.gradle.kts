import java.util.Properties

plugins {
    alias(libs.plugins.nexchat.android.application)
    alias(libs.plugins.nexchat.android.compose)
    alias(libs.plugins.nexchat.android.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}


android {
    namespace = "com.nexchat"
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    defaultConfig {
        applicationId = "com.nexchat"
        versionCode = 1
        versionName = "1.0.0"

        val certPinPrimary = localProperties.getProperty("CERT_PIN_PRIMARY") ?: "dummy_pin_1"
        val certPinBackup = localProperties.getProperty("CERT_PIN_BACKUP") ?: "dummy_pin_2"

        buildConfigField("String", "CERT_PIN_PRIMARY", "\"$certPinPrimary\"")
        buildConfigField("String", "CERT_PIN_BACKUP", "\"$certPinBackup\"")
        buildConfigField("String", "BASE_URL", "\"https://api.nexchat.app/api/v1/\"")
        buildConfigField("String", "WS_URL", "\"wss://api.nexchat.app/ws/chat\"")
    }
}

// Force WorkManager to our declared version (2.10.0) across the entire dependency graph.
// Without this, androidx.hilt:hilt-work transitively pulls in work-runtime:2.3.4 which
// does NOT include the FLAG_IMMUTABLE PendingIntent fix required for Android 12+ (API 31+),
// causing a fatal crash in WorkManager's ForceStopRunnable on launch.
configurations.all {
    resolutionStrategy {
        force("androidx.work:work-runtime:${libs.versions.workmanager.get()}")
        force("androidx.work:work-runtime-ktx:${libs.versions.workmanager.get()}")
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // ALL feature modules
    implementation(projects.feature.auth)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.chat)
    implementation(projects.feature.groups)
    implementation(projects.feature.media)
    implementation(projects.feature.contacts)
    implementation(projects.feature.backup)
    implementation(projects.feature.settings)

    // Core modules needed directly by app
    implementation(projects.core.common)
    implementation(projects.core.auth)
    implementation(projects.core.db)
    implementation(projects.core.network)
    implementation(projects.core.security)
    implementation(projects.core.work)
    implementation(projects.core.notification)
    implementation(projects.design)

    // Navigation + Activity + Splash
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation)
    implementation(libs.activity.compose)
    implementation(libs.splashscreen)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.serialization.json)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.datastore.preferences)

}
