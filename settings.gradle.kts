import org.gradle.kotlin.dsl.project

include(":feature:tabs:profile")


include(":feature:tabs:more")


include(":feature:tabs:favourite")


include(":feature:tabs:search")


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

rootProject.name = "Entertainment"
include(":app")
include(":feature:tabs:tmdb:toprated")
include(":feature:tabs:tmdb:popular")
include(":core")
include(":feature:tabs:tmdb:trending")
include(":feature:tabs:tmdb:presentation")
include(":feature:tabs:more")
include(":feature:tabs:profile")
include(":feature:tabs:favourite")
include(":feature:tabs:search")