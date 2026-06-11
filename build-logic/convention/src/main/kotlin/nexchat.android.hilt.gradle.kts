import com.nexchat.buildlogic.libs

plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

dependencies {
    add("implementation", libs.findLibrary("hilt-android").get())
    add("ksp", libs.findLibrary("hilt-compiler").get())
    add("implementation", libs.findLibrary("hilt-work").get())
    add("ksp", libs.findLibrary("hilt-work-compiler").get())
}
