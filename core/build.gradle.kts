plugins {
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

val shade = configurations.create("shade")
configurations {
    implementation.get().extendsFrom(shade)

    // Tests need paper-api alone. Inheriting `implementation` drags in the protection plugins,
    // several of which fail to resolve against paper-api.
    testImplementation.get().setExtendsFrom(emptyList())
    testRuntimeOnly.get().setExtendsFrom(emptyList())
}

dependencies {
    api(project(":api"))

    @Suppress("GradleDependency")
    compileOnly(libs.paper.api)

    // The only configuration packaged into the plugin jar, see `tasks.shadowJar` below.
    shade(libs.hikaricp)
    shade(libs.mariadb)
    shade(libs.bstats)
    compileOnly(libs.sqlite)

    compileOnly(libs.worldguard) // also supplies WorldEdit, for BukkitAdapter
    compileOnly(libs.shopgui) {
        exclude(group = "*")
    }
    compileOnly(libs.towny)
    compileOnly(libs.superiorskyblock)
    compileOnly(libs.vault)
    compileOnly(libs.excellenteconomy)
    compileOnly(libs.nightcore)
    compileOnly(libs.economyshopgui)
    compileOnly(libs.bentobox)
    compileOnly(libs.redprotect.core) {
        exclude(group = "*")
    }
    compileOnly(libs.redprotect.spigot) {
        exclude(group = "*")
    }
    // slate drags in adventure-platform-bukkit and five transitives; nothing here compiles against it.
    compileOnly(libs.auraskills) {
        exclude(group = "dev.aurelium", module = "slate")
    }
    compileOnly(libs.minecodes.plots)
    compileOnly(libs.zshop)
    compileOnly(libs.zmenu)

    implementation(libs.griefprevention)
    implementation(libs.lands)
    implementation(libs.simpleclaimsystem.api)
    implementation(libs.simpleclaimsystem)
    // Otherwise pulls BigDoors, dynmap-api and an older WorldEdit/WorldGuard pair.
    implementation(libs.residence) {
        isTransitive = false
    }

    compileOnly(libs.mythicmobs)
    compileOnly(libs.iridiumskyblock)
    compileOnly(libs.factions)
    compileOnly(libs.blocklocker)
    compileOnly(libs.mmoitems) { isTransitive = false }
    compileOnly(libs.mythiclib) { isTransitive = false }

    // Pinned directly: com.intellectualsites.bom:bom-newest would downgrade paper-api and guava.
    // adventure comes from paper-api.
    compileOnly(libs.plotsquared) {
        exclude(group = "net.kyori")
    }

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // paper-api is here for YamlConfiguration only; nothing under test needs a live server.
    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-nowarn", "-Xlint:-deprecation"))
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

// Don't use 'jar' task to build plugin jar, use 'shadowJar' task instead
tasks.jar {
    archiveBaseName.set("SmartSpawnerJar")
    archiveVersion.set(version.toString())

    from(project(":api").sourceSets["main"].output)
    from(sourceSets["main"].output)
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}

tasks.shadowJar {

    archiveBaseName.set("SmartSpawner")
    archiveVersion.set(version.toString())
    archiveClassifier.set("")
    from(project(":api").sourceSets["main"].output)
    configurations = listOf(shade)

    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    exclude("META-INF/maven/**")
    exclude("META-INF/MANIFEST.MF")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/NOTICE*")
    from(sourceSets["main"].output)
    exclude("org/slf4j/**")

    relocate("com.zaxxer.hikari", "github.nighter.smartspawner.libs.hikari")
    relocate("org.mariadb.jdbc", "github.nighter.smartspawner.libs.mariadb")
    relocate("org.bstats", project.group.toString())
    mergeServiceFiles()
    // destinationDirectory.set(file("C:\\Users\\Admin\\Desktop\\TestServer\\plugins"))
}

tasks.build {
    dependsOn(tasks.shadowJar)
}


tasks.runServer {
    minecraftVersion("26.1.2")
    runDirectory.set(rootProject.layout.projectDirectory.dir("run"))
    // Minecraft bundles JOML 1.10.8, whose Unsafe path is deprecated on Java 25.
    // Prefer JOML's NIO implementation and allow remaining upstream users (such as spark)
    // until Paper updates them, preventing Java 25's terminal-deprecation warning block.
    jvmArgs("-Djoml.nounsafe=true", "--sun-misc-unsafe-memory-access=allow")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
        expand(props)
    }
}
