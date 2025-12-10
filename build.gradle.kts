import java.util.*

plugins {
    id("java-library")
    id("maven-publish")
}

group = "fr.traqueur"
version = property("version") as String

extra.set("targetFolder", file("target/"))
extra.set("classifier", System.getProperty("archive.classifier"))
extra.set("sha", System.getProperty("github.sha"))

rootProject.extra.properties["sha"]?.let { sha ->
    version = sha
}

allprojects {
    apply {
        plugin("java-library")
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        api("org.slf4j:slf4j-api:2.0.9")
        api("com.zaxxer:HikariCP:5.1.0")

        testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
        testImplementation("org.assertj:assertj-core:3.24.2")
        testImplementation("org.mockito:mockito-core:5.7.0")
        testImplementation("org.mockito:mockito-junit-jupiter:5.7.0")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-parameters")
    }

    tasks.test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }

        jvmArgs(
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED"
        )
    }
}

dependencies {
    testImplementation("org.testcontainers:junit-jupiter:1.19.0")
}

afterEvaluate {
    project(":dialects").subprojects.forEach { dialectProject ->
        dependencies {
            testImplementation(dialectProject)
        }
    }
}

tasks.register("generateVersionProperties") {
    doLast {
        val name = project.name.lowercase(Locale.getDefault())
        val file = project.file("src/main/resources/$name.properties")
        file.parentFile?.mkdirs()
        file.writeText("version=${project.version}")
    }
}

// Tâche custom pour créer le JAR all-dialects (fusion de tous les dialectes)
val allDialectsJar by tasks.registering(Jar::class) {
    archiveBaseName.set(rootProject.name+"-all-dialects")
    archiveVersion.set(rootProject.version.toString())
    archiveClassifier.set("")
    destinationDirectory.set(rootProject.extra["targetFolder"] as File)

    // Inclure uniquement les classes compilées des dialectes
    project(":dialects").subprojects.forEach { dialectProject ->
        from(dialectProject.sourceSets.main.get().output)
        dependsOn(dialectProject.tasks.named("classes"))
    }

    // Fusionner les fichiers META-INF/services pour le SPI
    filesMatching("META-INF/services/*") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    // Stratégie par défaut pour les autres fichiers
    duplicatesStrategy = DuplicatesStrategy.WARN

    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to "Victor All Dialects",
                "Implementation-Version" to rootProject.version,
                "Implementation-Vendor" to "fr.traqueur"
            )
        )
    }

    // Fusion manuelle des fichiers SPI
    doLast {
        val jarFile = archiveFile.get().asFile
        val tempDir = file("${buildDir}/tmp/all-dialects-merge")
        tempDir.deleteRecursively()
        tempDir.mkdirs()
        ant.invokeMethod("unzip", mapOf("src" to jarFile, "dest" to tempDir))
        val servicesDir = file("${tempDir}/META-INF/services")
        if (servicesDir.exists()) {
            servicesDir.listFiles()?.forEach { serviceFile ->
                if (serviceFile.isFile) {
                    val implementations = mutableSetOf<String>()
                    project(":dialects").subprojects.forEach { dialectProject ->
                        val dialectServiceFile =
                            file("${dialectProject.buildDir}/resources/main/META-INF/services/${serviceFile.name}")
                        if (dialectServiceFile.exists()) {
                            implementations.addAll(dialectServiceFile.readLines().filter { it.isNotBlank() })
                        }
                    }
                    serviceFile.writeText(implementations.joinToString("\n") + "\n")
                }
            }
        }

        // Re-créer le JAR
        ant.invokeMethod(
            "jar", mapOf(
                "destfile" to jarFile,
                "basedir" to tempDir,
                "manifest" to file("${tempDir}/META-INF/MANIFEST.MF")
            )
        )

        tempDir.deleteRecursively()
    }

}

tasks.build {
    dependsOn(allDialectsJar)
}

tasks.jar {
    archiveClassifier.set("")
    destinationDirectory.set(rootProject.extra["targetFolder"] as File)
}

tasks.processResources {
    dependsOn("generateVersionProperties")
}

java {
    withSourcesJar()
    withJavadocJar()
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
            from(components["java"])
            groupId = "fr.traqueur.victor"
            artifactId = "victor-core"
            version = rootProject.version.toString()
        }

        // Publication du JAR all-dialects (fusion de tous les dialectes sans le core)
        create<MavenPublication>("allDialects") {
            artifact(allDialectsJar)
            groupId = "fr.traqueur.victor"
            artifactId = "all-dialects"
            version = rootProject.version.toString()
        }
    }
}