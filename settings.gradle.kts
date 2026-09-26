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

rootProject.name = "CampusMaps"

// app    = the phone app (S1, S1b, S2, S3, S4, Settings, Debug)
// wear   = the Galaxy Watch app (section 12 of the design handoff)
// shared = plain Kotlin code both apps need (watch step format and haptics)
include(":app", ":wear", ":shared")
