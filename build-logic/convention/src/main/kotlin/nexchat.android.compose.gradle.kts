import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.nexchat.buildlogic.libs

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.findByType<ApplicationExtension>()?.apply {
    buildFeatures {
        compose = true
    }
}
extensions.findByType<LibraryExtension>()?.apply {
    buildFeatures {
        compose = true
    }
}

pluginManager.withPlugin("com.android.base") {
    dependencies {
        val composeBom = platform(libs.findLibrary("compose-bom").get())
        add("implementation", composeBom)
        add("androidTestImplementation", composeBom)
        add("implementation", libs.findBundle("compose").get())
    }
}
