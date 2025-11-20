package fr.traqueur.victor.types;

import fr.traqueur.victor.exceptions.VictorConfigurationException;
import fr.traqueur.victor.exceptions.VictorValidationException;

public enum VictorDialect {

    SQLITE("org.sqlite.JDBC", "sqlite"),
    MYSQL("com.mysql.cj.jdbc.Driver", "mysql"),
    MARIADB("org.mariadb.jdbc.Driver", "mariadb"),
    POSTGRESQL("org.postgresql.Driver", "postgresql"),
    H2("org.h2.Driver", "h2");
    
    private final String driverClassName;
    private final String urlPrefix;

    VictorDialect(String driverClassName, String urlPrefix) {
        this.driverClassName = driverClassName;
        this.urlPrefix = urlPrefix;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    public boolean isEmbedded() {
        return this == SQLITE || this == H2;
    }

    public boolean supportsJsonType() {
        return this == MYSQL || this == MARIADB || this == POSTGRESQL;
    }

    public boolean supportsSchemas() {
        return this == POSTGRESQL || this == H2;
    }

    public boolean supportsSequences() {
        return this == POSTGRESQL || this == H2;
    }

    public static VictorDialect fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
            throw new VictorValidationException("URL JDBC invalid : " + jdbcUrl);
        }
        
        String protocol = jdbcUrl.substring(5);
        for (VictorDialect dialect : values()) {
            if (protocol.startsWith(dialect.urlPrefix + ":")) {
                return dialect;
            }
        }
        throw new VictorConfigurationException(
            "Dialects not supported for URL : " + jdbcUrl);
    }
}