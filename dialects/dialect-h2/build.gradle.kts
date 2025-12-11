description = "Victor H2 Database Dialect"

dependencies {
    api("com.h2database:h2:2.2.224")
}

tasks.test {
    systemProperty("victor.test.dialect", "h2")
    systemProperty("victor.test.url", "jdbc:h2:mem:testdb")
}