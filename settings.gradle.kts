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

rootProject.name = "kanshu"
include(":app")
include(":core")

file("features")
    .listFiles()
    ?.filter { it.isDirectory }
    ?.forEach { feature ->
        feature.listFiles()
            ?.filter { platform ->
                platform.isDirectory && platform.resolve("build.gradle.kts").isFile
            }
            ?.forEach { platform ->
                include(":features:${feature.name}:${platform.name}")
            }
    }
