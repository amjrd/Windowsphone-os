pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_ROOT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MetroLauncher"
include(":app")
