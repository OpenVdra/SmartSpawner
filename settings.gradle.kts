pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    // mc-run is served by JitPack, which rewrites the groupId, so the plugin marker does not
    // resolve. Map the plugin id to the JitPack module by hand.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.github.n1ght3r.mc-run") {
                useModule("com.github.n1ght3r:mc-run:${requested.version}")
            }
        }
    }
}

plugins {
    // Downloads the JDK that runServer/runClient need (25 for 26.x) when missing.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "SmartSpawner"
include("core")
include("api")

