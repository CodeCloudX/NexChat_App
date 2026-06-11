plugins {
    `kotlin-dsl`
}

group = "com.nexchat.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.room.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
    implementation(libs.google.services.gradle.plugin)
    implementation(libs.crashlytics.gradle.plugin)
    implementation("com.squareup:javapoet:1.13.0")
}
