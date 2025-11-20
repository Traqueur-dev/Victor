package fr.traqueur.victor;

import fr.traqueur.victor.exceptions.VictorConfigurationException;
import fr.traqueur.victor.types.VictorDialect;

import java.util.*;

public final class VictorBuilder {

    private VictorDialect dialect;
    private String host;
    private int port;
    private String database;
    private String file;
    private String username;
    private String password;
    private String customUrl;
    private boolean autoMigrate = false;
    private boolean showSql = false;
    private final Properties properties = new Properties();
    private final Set<Class<?>> entityClasses = new HashSet<>();

    public VictorBuilder sqlite() {
        this.dialect = VictorDialect.SQLITE;
        return this;
    }

    public VictorBuilder mysql() {
        this.dialect = VictorDialect.MYSQL;
        this.port = 3306;
        return this;
    }

    public VictorBuilder postgresql() {
        this.dialect = VictorDialect.POSTGRESQL;
        this.port = 5432;
        return this;
    }

    public VictorBuilder mariadb() {
        this.dialect = VictorDialect.MARIADB;
        this.port = 3306;
        return this;
    }

    public VictorBuilder h2() {
        this.dialect = VictorDialect.H2;
        return this;
    }

    public VictorBuilder host(String host) {
        this.host = host;
        return this;
    }

    public VictorBuilder port(int port) {
        this.port = port;
        return this;
    }

    public VictorBuilder database(String database) {
        this.database = database;
        return this;
    }

    public VictorBuilder file(String file) {
        this.file = file;
        return this;
    }

    public VictorBuilder url(String url) {
        this.customUrl = url;
        return this;
    }

    public VictorBuilder credentials(String username, String password) {
        this.username = username;
        this.password = password;
        return this;
    }

    public VictorBuilder autoMigrate() {
        this.autoMigrate = true;
        return this;
    }

    public VictorBuilder autoMigrate(boolean autoMigrate) {
        this.autoMigrate = autoMigrate;
        return this;
    }

    public VictorBuilder showSql() {
        this.showSql = true;
        return this;
    }

    public VictorBuilder showSql(boolean showSql) {
        this.showSql = showSql;
        return this;
    }

    public VictorBuilder property(String key, String value) {
        properties.setProperty(key, value);
        return this;
    }

    public VictorBuilder entities(Class<?>... entityClasses) {
        this.entityClasses.addAll(Arrays.asList(entityClasses));
        return this;
    }

    public VictorBuilder autoDetectDialect() {
        if (customUrl != null) {
            this.dialect = VictorDialect.fromJdbcUrl(customUrl);
        }
        return this;
    }

    public Victor build() {
        validate();

        VictorConfiguration config = createConfiguration();
        VictorEngine engine = new VictorEngine(config);

        if (autoMigrate) {
            engine.runMigrations();
        }

        return Victor.create(engine);
    }

    private void validate() {
        if (dialect == null) {
            throw new VictorConfigurationException("Database dialect must be specified");
        }

        if (customUrl == null) {
            // Pour les bases embarquées, vérifier file OU database
            if (dialect.isEmbedded()) {
                if (file == null && database == null) {
                    throw new VictorConfigurationException("File or database name must be specified for " + dialect);
                }
            } else {
                // Pour les bases non-embarquées, host est obligatoire
                if (host == null) {
                    throw new VictorConfigurationException("Host must be specified for " + dialect);
                }
                if (database == null) {
                    throw new VictorConfigurationException("Database name required for " + dialect);
                }
            }
        }
    }

    private VictorConfiguration createConfiguration() {
        String connectionUrl = buildConnectionUrl();

        return new VictorConfiguration(
                dialect,
                connectionUrl,
                username,
                password,
                properties,
                showSql,
                autoMigrate,
                entityClasses
        );
    }

    private String buildConnectionUrl() {
        if (customUrl != null) {
            return customUrl;
        }

        return switch (dialect) {
            case SQLITE -> "jdbc:sqlite:" + file;
            case H2 -> file != null ? "jdbc:h2:" + file : "jdbc:h2:mem:" + database;
            case MYSQL -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                    host, port, database);
            case MARIADB -> String.format("jdbc:mariadb://%s:%d/%s", host, port, database);
            case POSTGRESQL -> String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
        };
    }
}