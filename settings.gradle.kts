pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Remove the jetbrains compose repository
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Remove the jetbrains compose repository
    }
}

rootProject.name = "QRCodeScanner"
include(":app")
