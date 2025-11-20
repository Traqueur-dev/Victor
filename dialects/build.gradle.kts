// dialects/build.gradle.kts

// Configuration parent pour tous les dialectes
subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.gradleup.shadow")

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

    // Shadow JAR pour chaque dialecte individuellement
    tasks.shadowJar {
        archiveClassifier.set("")
        destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))

        // Merger les services pour ce dialecte
        mergeServiceFiles()
    }
}