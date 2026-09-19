plugins {
    java
    `java-library`
    `maven-publish`
    alias(libs.plugins.shadow) apply false
}

val targetJavaVersion = 25

// Forced rather than constrained: only a forced version beats the `strictly` constraints
// WorldEdit and PlotSquared declare.
val serverProvided = libs.bundles.serverProvided.get().map {
    "${it.module.group}:${it.module.name}:${it.versionConstraint.requiredVersion}"
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    group = "github.nighter"
    version = "1.8.2"

    repositories {
        mavenCentral()
        maven {
            name = "papermc-repo"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
        maven {
            name = "sonatype-public"
            url = uri("https://oss.sonatype.org/content/groups/public/")
        }
        maven {
            name = "jitpack"
            url = uri("https://jitpack.io")
        }
        maven {
            name = "enginehub"
            url = uri("https://maven.enginehub.org/repo/")
        }
        ivy {
            name = "townyGitHubReleases"
            url = uri("https://github.com/TownyAdvanced/Towny/releases/download")
            patternLayout {
                artifact("[revision]/[artifact]-[revision].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("com.palmergames.bukkit.towny", "towny")
            }
        }
        maven {
            name = "bg-repo"
            url = uri("https://repo.bg-software.com/repository/api/")
        }
        maven {
            name = "codemc"
            url = uri("https://repo.codemc.io/repository/bentoboxworld/")
        }
        maven {
            name = "nightexpress-releases"
            url = uri("https://repo.nightexpressdev.com/releases")
        }
        ivy {
            name = "iridiumSkyblockGitHubReleases"
            url = uri("https://github.com/Iridium-Development/IridiumSkyblock/releases/download")
            patternLayout {
                artifact("[revision]/[artifact]-[revision].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("com.iridium", "IridiumSkyblock")
            }
        }
        maven {
            name = "lumineReleases"
            url = uri("https://mvn.lumine.io/repository/maven-public/")
            content {
                includeGroup("io.lumine")
            }
        }
        maven {
            name = "groupez"
            url = uri("https://repo.groupez.dev/releases")
        }
        maven {
            name = "minecodes-repository-releases"
            url = uri("https://maven.minecodes.pl/releases")
        }
        maven {
            name = "william278Releases"
            url = uri("https://repo.william278.net/releases")
        }
        maven {
            name = "factionsuuid"
            url = uri("https://dependency.download/releases")
            content {
                includeGroup("dev.kitteh")
            }
        }
        maven {
            name = "codemc-public"
            url = uri("https://repo.codemc.org/repository/maven-public/")
            content {
                includeGroup("nl.rutgerkok")
            }
        }
        maven {
            name = "phoenixdevt"
            url = uri("https://nexus.phoenixdevt.fr/repository/maven-public/")
            content {
                includeGroup("net.Indyuce")
                includeModule("io.lumine", "MythicLib-dist")
            }
        }
    }

    // Compile, runtime and their test counterparts. `shade` is left unmatched so the shaded jar
    // packages exactly what is declared.
    configurations.matching { it.name.lowercase().endsWith("classpath") }.configureEach {
        resolutionStrategy.force(*serverProvided.toTypedArray())
    }

    java {
        val javaVersion = JavaVersion.toVersion(targetJavaVersion)
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        if (JavaVersion.current() < javaVersion) {
            toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(targetJavaVersion)
    }
}

subprojects {
    apply(plugin = "java-library")

    java {
        withJavadocJar()
        withSourcesJar()
    }
}
