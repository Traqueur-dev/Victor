import java.util.*

plugins {
    id("java-library")
    id("re.alwyn974.groupez.publish") version "1.0.0"
    id("com.gradleup.shadow") version "9.0.0-beta11"
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
    apply { plugin("java-library") }

    repositories {
        mavenCentral()
    }

    dependencies {
        api("org.slf4j:slf4j-api:2.0.9")

        testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.test {
        useJUnitPlatform()
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

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
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

publishConfig {
    githubOwner = "Traqueur-dev"
    useRootProjectName = true
}
