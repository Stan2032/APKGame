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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GravityGambit"
include(":core")
// :app needs AGP (Google Maven) and the Android SDK. Environments that can't
// reach dl.google.com can still build and test the :core engine by setting
// GG_SKIP_ANDROID=1. CI and normal dev builds are unaffected.
if (System.getenv("GG_SKIP_ANDROID") != "1") {
    include(":app")
}
