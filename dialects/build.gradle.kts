dependencies {
    api(rootProject)
}

subprojects {
    dependencies {
        api(parent!!)
    }
}