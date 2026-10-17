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
        // WebRTC prebuilt (Stream.io maintained fork of Google's libwebrtc)
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "LinkControl"
include(":app")
