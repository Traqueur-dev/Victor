package fr.traqueur.victor;

import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.entity.dialect.Dialect;
import fr.traqueur.victor.exceptions.VictorConfigurationException;
import fr.traqueur.victor.registries.DialectRegistry;
import fr.traqueur.victor.scanner.EntityScanner;
import fr.traqueur.victor.security.SecureCredentials;

import java.util.*;

public final class VictorBuilder {

    private Dialect dialect;
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
    private final Set<Class<? extends Entity<?>>> entityClasses = new HashSet<>();

    public VictorBuilder sqlite() {
        this.dialect = DialectRegistry.getInstance().getByName("sqlite");
        return this;
    }

    public VictorBuilder mysql() {
        this.dialect = DialectRegistry.getInstance().getByName("mysql");
        this.port = 3306;
        return this;
    }

    public VictorBuilder postgresql() {
        this.dialect = DialectRegistry.getInstance().getByName("postgresql");
        this.port = 5432;
        return this;
    }

    public VictorBuilder mariadb() {
        this.dialect = DialectRegistry.getInstance().getByName("mariadb");
        this.port = 3306;
        return this;
    }

    public VictorBuilder h2() {
        this.dialect = DialectRegistry.getInstance().getByName("h2");
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

    /**
     * Sets database credentials using username and password strings.
     *
     * <p><b>Security Note:</b> String passwords are stored in memory and cannot be cleared.
     * For better security, consider using {@link #secureCredentials(SecureCredentials)}.
     *
     * @param username the database username
     * @param password the database password
     * @return this builder
     */
    public VictorBuilder credentials(String username, String password) {
        this.username = username;
        this.password = password;
        return this;
    }

    /**
     * Sets database credentials using SecureCredentials for enhanced security.
     *
     * <p>SecureCredentials stores passwords as char[] which can be cleared from memory,
     * providing better security than String-based passwords.
     *
     * @param credentials the secure credentials (will be copied)
     * @return this builder
     */
    public VictorBuilder secureCredentials(SecureCredentials credentials) {
        try (credentials) {
            this.username = credentials.getUsername();
            this.password = credentials.getPasswordAsString();
        }
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

    @SafeVarargs
    public final VictorBuilder entities(Class<? extends Entity<?>>... entityClasses) {
        this.entityClasses.addAll(Arrays.asList(entityClasses));
        return this;
    }

    public VictorBuilder autoScanEntities() {
        this.entityClasses.addAll(EntityScanner.scanForEntities());
        return this;
    }

    public VictorBuilder autoScanEntities(ClassLoader classLoader) {
        this.entityClasses.addAll(EntityScanner.scanForEntities(classLoader));
        return this;
    }

    public VictorBuilder autoScanEntities(String... packages) {
        for (String pkg : packages) {
            this.entityClasses.addAll(EntityScanner.scanForEntities(pkg));
        }
        return this;
    }

    public VictorBuilder autoScanEntities(ClassLoader classLoader, String... packages) {
        for (String pkg : packages) {
            this.entityClasses.addAll(EntityScanner.scanForEntities(pkg, classLoader));
        }
        return this;
    }

    public VictorBuilder autoDetectDialect() {
        if (customUrl != null) {
            this.dialect = DialectRegistry.getInstance().detectFromUrl(customUrl);
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
            if (dialect.isEmbedded()) {
                if (file == null && database == null) {
                    throw new VictorConfigurationException("File or database name must be specified for " + dialect.getName());
                }
            } else {
                if (host == null) {
                    throw new VictorConfigurationException("Host must be specified for " + dialect.getName());
                }
                if (database == null) {
                    throw new VictorConfigurationException("Database name required for " + dialect.getName());
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
        if (dialect.isEmbedded()) {
            String dbName = file != null ? file : database;
            return dialect.buildConnectionUrl(null, 0, dbName, properties);
        } else {
            return dialect.buildConnectionUrl(host, port, database, properties);
        }
    }
}