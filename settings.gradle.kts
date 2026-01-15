rootProject.name = "Victor"

pluginManagement {
    repositories {
        maven {
            name = "groupezReleases"
            url = uri("https://repo.groupez.dev/releases")
        }
        gradlePluginPortal()
    }
}

include(":dialects")
include(":bom")

file("dialects").listFiles()?.forEach { file ->
    if (file.isDirectory && !file.name.equals("build") && !file.name.equals("src")) {
        println("Include dialects:${file.name}")
        include(":dialects:${file.name}")
    }
}