description = "Victor MySQL Database Dialect"

dependencies {
    api("com.mysql:mysql-connector-j:8.2.0")
    testImplementation("com.mysql:mysql-connector-j:8.2.0")
}

tasks.test {
    systemProperty("victor.test.dialect", "mysql")
    systemProperty("victor.test.url", "jdbc:mysql://localhost:3306/testdb")
}