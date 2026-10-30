pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.google\\.testing\\.platform.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.google\\.testing\\.platform.*")
            }
        }
        mavenCentral()
    }
}
rootProject.name = "PersonalAIOS"
include(":app", ":core:ui", ":core:shape", ":feature:today", ":feature:diary", ":feature:me")
include(":domain", ":core:theme", ":data:weather")
include(":data:local")
include(":core:weather")
include(":core:glass")
include(":data:device")
include(":feature:calendar")

