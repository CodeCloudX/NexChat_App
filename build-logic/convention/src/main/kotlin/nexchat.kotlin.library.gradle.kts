import com.nexchat.buildlogic.libs
import org.gradle.api.plugins.JavaPluginExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

extensions.configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    add("implementation", libs.findLibrary("timber").get())
}
