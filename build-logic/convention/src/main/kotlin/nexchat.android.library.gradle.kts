import com.android.build.api.dsl.LibraryExtension
import com.nexchat.buildlogic.libs

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

extensions.configure<LibraryExtension>("android") {
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    add("implementation", libs.findLibrary("timber").get())
    add("testImplementation", libs.findLibrary("junit").get())
    add("testImplementation", libs.findLibrary("mockk").get())
    add("testImplementation", libs.findLibrary("truth").get())
    add("testImplementation", libs.findLibrary("coroutines-test").get())
}
