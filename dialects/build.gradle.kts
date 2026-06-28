// dialects/build.gradle.kts

// Configuration parent pour tous les dialectes
subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = rootProject.group
    version = rootProject.version

    // Chaque dialecte dépend du core Victor
    dependencies {
        api(rootProject)

        // Access to test classes from root project (AbstractDialectTest, User, UserRepository, etc.)
        testImplementation(rootProject.sourceSets.test.get().output)

        // Testcontainers for integration tests (aligned with root: BOM 2.0.5 -> docker-java 3.7.x,
        // required for Docker Engine API >= 1.40).
        testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
        testImplementation("org.testcontainers:testcontainers")
        testImplementation("org.testcontainers:testcontainers-junit-jupiter")
        testImplementation("org.testcontainers:testcontainers-mysql")
        testImplementation("org.testcontainers:testcontainers-postgresql")
    }

    tasks.matching { it.name == "compileTestJava" || it.name == "test" }.configureEach {
        dependsOn(rootProject.tasks.named("testClasses"))
    }

    java {
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<Test>().configureEach {
        dependsOn(rootProject.tasks.named("testClasses"))
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
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()
            }
        }
    }
}