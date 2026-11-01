pluginManagement {
    repositories {
        google()
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
rootProject.name = "AmpliDrive"
include(":app")
// Play install-time asset pack with the offline speech models (filled by CI; see voicemodels/build.gradle.kts).
include(":voicemodels")
