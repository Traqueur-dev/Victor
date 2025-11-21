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

file("dialects").listFiles()?.forEach { file ->
    if (file.isDirectory and !file.name.equals("build") && !file.name.equals("src")) {
        println("Include dialects:${file.name}")
        include(":dialects:${file.name}")
    }
}

include("dialects:dialect-mysql")