plugins {
    java
    `java-library`
    `maven-publish`
    id("com.gradleup.shadow") version "9.4.2" apply false
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    group = "github.nighter"
    version = "1.6.9"

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
            name = "opencollabRepositoryMain"
            url = uri("https://repo.opencollab.dev/main")
        }
        maven {
            name = "jitpack"
            url = uri("https://jitpack.io")
        }
        maven {
            name = "enginehub"
            url = uri("https://maven.enginehub.org/repo/")
        }
        maven {
            name = "glaremasters repo"
            url = uri("https://repo.glaremasters.me/repository/towny/")
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
        maven {
            name = "iridiumdevelopment"
            url = uri("https://nexus.iridiumdevelopment.net/repository/maven-releases/")
        }
        maven {
            name = "Lumine Releases"
            url = uri("https://mvn.lumine.io/repository/maven-public/")
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
    }
}

subprojects {
    apply(plugin = "java-library")

    java {
        withJavadocJar()
        withSourcesJar()
    }
}

val targetJavaVersion = 25
allprojects {
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
        if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
            options.release.set(targetJavaVersion)
        }
    }
}
