// The agent client: a Fabric client mod that answers a file queue of JSON requests, so an agent can
// drive a real Minecraft client against the dev Paper server and capture the framebuffer. See README.md.
plugins {
    // 26.1+ ships unobfuscated, so this is the plain Loom: no mappings, no remapping.
    id("net.fabricmc.fabric-loom") version "1.17.21"
}

// Must match mcRun.minecraftVersion in core/build.gradle.kts, or the client cannot join.
val minecraftVersion = "26.2"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:0.19.5")
}

loom {
    runs {
        named("client") {
            // Joins the plugin's dev server (./gradlew runServer at the repository root) straight from
            // launch, as the same offline player runClient there uses, so ops.json and the plugin's
            // data line up between the two clients.
            val address = providers.gradleProperty("server").orElse("localhost:25565").get()
            programArgs("--username", providers.gradleProperty("username").orElse("Dev").get())
            if (address != "none") programArgs("--quickPlayMultiplayer", address)
            // -PagentName=<name> gives a second client a queue of its own (run/agent/<name>/), for
            // a scene with two players in it: ./gradlew runClient -Pusername=Steve -PagentName=steve
            providers.gradleProperty("agentName").orNull?.let { property("smartspawner.agent", it) }
            // Maximised window, pointer never grabbed. See ClientWindow.
            property("smartspawner.agent.driven", "true")
            // -Pagent=<file> answers that file once the client is up, then closes it.
            providers.gradleProperty("agent").orNull?.let { script ->
                property("smartspawner.agent.script", rootProject.file(script).absolutePath)
            }
        }
        // The client is all this is; a server run would only confuse `gradlew tasks`.
        remove(findByName("server"))
    }
}

tasks.processResources {
    inputs.property("minecraftVersion", minecraftVersion)
    filesMatching("fabric.mod.json") { expand("minecraftVersion" to minecraftVersion) }
}
