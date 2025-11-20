description = "Victor SQLite Database Dialect"

dependencies {
    api("org.xerial:sqlite-jdbc:3.44.1.0")
    testImplementation("org.xerial:sqlite-jdbc:3.44.1.0")
}

tasks.test {
    systemProperty("victor.test.dialect", "sqlite")
    systemProperty("victor.test.url", "jdbc:sqlite::memory:")
}