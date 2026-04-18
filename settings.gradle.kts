rootProject.name = "angzarr-examples-java"

// Use the canonical client library via git submodule composite build
includeBuild("angzarr-client-java") {
    dependencySubstitution {
        substitute(module("dev.angzarr:client")).using(project(":client"))
        substitute(module("dev.angzarr:proto")).using(project(":proto"))
    }
}

// Player domain
include("player-agg")
project(":player-agg").projectDir = file("player/agg")
include("player-upc")
project(":player-upc").projectDir = file("player/upc")
include("player-saga-table")
project(":player-saga-table").projectDir = file("player/saga-table")

// Table domain
include("table-agg")
project(":table-agg").projectDir = file("table/agg")
include("table-saga-hand")
project(":table-saga-hand").projectDir = file("table/saga-hand")
include("table-saga-player")
project(":table-saga-player").projectDir = file("table/saga-player")

// Hand domain
include("hand-agg")
project(":hand-agg").projectDir = file("hand/agg")
include("hand-saga-table")
project(":hand-saga-table").projectDir = file("hand/saga-table")
include("hand-saga-player")
project(":hand-saga-player").projectDir = file("hand/saga-player")

// Process Manager
include("hand-flow")

// Tournament domain
include("tournament-agg")
project(":tournament-agg").projectDir = file("tournament/agg")

// Hand upcaster
include("hand-upc")
project(":hand-upc").projectDir = file("hand/upc")

// Projector
include("prj-output")

// Tests — TODO(tier5-port): the Cucumber step defs here use the old OO
// aggregate API (player.handleCommand / player.rehydrate / OO state accessors)
// and need to be ported to the Tier 5 Router-based pattern. Excluded from the
// composite build until that work happens.
// include("tests")

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
