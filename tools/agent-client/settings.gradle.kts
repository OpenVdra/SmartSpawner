// A build of its own, not a module of the plugin's: the plugin compiles against paper-api, this
// against the Minecraft client through Loom, and the two have nothing on a classpath in common.
// Run it with the repository's wrapper: ./gradlew -p tools/agent-client runClient
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "smartspawner-agent-client"
