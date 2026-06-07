dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "github.nighter"
            artifactId = "smartspawner-api"
            from(components["java"])

            pom {
                name.set("SmartSpawner API")
                description.set("API for SmartSpawner plugin - allows other plugins to create and manage spawners")
                url.set("https://github.com/NighterDevelopment/SmartSpawner")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("nighter")
                        name.set("Nighter")
                        email.set("notnighter@gmail.com")
                    }
                }
            }
        }
    }
}
