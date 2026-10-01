rootProject.name = "angzarr-examples-java"

// Use the canonical client library via git submodule composite build
includeBuild("angzarr-client-java") {
    dependencySubstitution {
        substitute(module("dev.angzarr:client")).using(project(":client"))
        substitute(module("dev.angzarr:proto")).using(project(":proto"))
    }
}

// Example subprojects are included here.

// Configure proto path resolution
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
