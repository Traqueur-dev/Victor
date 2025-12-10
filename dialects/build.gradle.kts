// dialects/build.gradle.kts

// Configuration parent pour tous les dialectes
subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = parent!!.group
    version = parent!!.version

    // Chaque dialecte dépend du core Victor
    dependencies {
        api(rootProject)
    }

    java {
        withSourcesJar()
        withJavadocJar()
    }

    tasks.jar {
        // Préserver les META-INF/services
        duplicatesStrategy = DuplicatesStrategy.WARN

        manifest {
            attributes(
                mapOf(
                    "Implementation-Version" to project.version,
                    "Implementation-Vendor" to "fr.traqueur"
                )
            )
        }
    }

    configure<PublishingExtension> {
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
                from(components["java"])
                groupId = "fr.traqueur.victor"
                artifactId = project.name
                version = rootProject.version.toString()
            }
        }
    }
}