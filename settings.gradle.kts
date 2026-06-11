pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "NexChatApp"

includeBuild("build-logic")

include(":app")

include(
    ":core:common",
    ":core:auth",
    ":core:db",
    ":core:network",
    ":core:security",
    ":core:media",
    ":core:storage",
    ":core:notification",
    ":core:work",
    ":core:ui"
)

include(":design")

include(
    ":feature:auth",
    ":feature:onboarding",
    ":feature:chat",
    ":feature:groups",
    ":feature:media",
    ":feature:contacts",
    ":feature:backup",
    ":feature:settings"
)