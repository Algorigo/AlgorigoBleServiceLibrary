pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven(url="https://jitpack.io")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url="https://jitpack.io")
    }
}

rootProject.name = "AlgorigoBleServiceLibrary"
include(":app")
include(":algorigobleservice")
include(":test_app")
