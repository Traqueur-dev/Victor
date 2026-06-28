plugins {
    id("java-platform")
    id("maven-publish")
}

description = "Victor BOM (Bill of Materials)"

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        // Core module
        api(project(":"))

        // Individual dialects
        api(project(":dialects:dialect-h2"))
        api(project(":dialects:dialect-mysql"))
        api(project(":dialects:dialect-postregresql"))
        api(project(":dialects:dialect-sqlite"))
    }
}

publishing {
    repositories {
        maven {
            val repository = System.getProperty("repository.name", "snapshots")
            val repoType = repository.lowercase()

            name = "groupez${repository.replaceFirstChar { it.uppercase() }}"
            url = uri("https://repo.groupez.dev/$repoType")

            credentials {
                username = findProperty("${name}Username") as String?
                    ?: System.getenv("MAVEN_USERNAME")
                password = findProperty("${name}Password") as String?
                    ?: System.getenv("MAVEN_PASSWORD")
            }

            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])

            groupId = "fr.traqueur.victor"
            artifactId = "bom"
            version = rootProject.version.toString()

            pom {
                name.set("Victor BOM")
                description.set("Victor Bill of Materials - Manages consistent versions across Victor modules")
                url.set("https://github.com/Traqueur-dev/Victor")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("traqueur")
                        name.set("Traqueur")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/Traqueur-dev/Victor.git")
                    developerConnection.set("scm:git:ssh://github.com/Traqueur-dev/Victor.git")
                    url.set("https://github.com/Traqueur-dev/Victor")
                }
            }
        }
    }
}