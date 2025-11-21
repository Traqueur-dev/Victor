description = "Victor PostgreSQL Database Dialect"

dependencies {
    api("org.postgresql:postgresql:42.7.1")
    testImplementation("org.postgresql:postgresql:42.7.1")
}

tasks.test {
    systemProperty("victor.test.dialect", "postgresql")
    systemProperty("victor.test.url", "jdbc:postgresql://localhost:5432/testdb")
}