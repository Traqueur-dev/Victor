package fr.traqueur.victor.managers;

import fr.traqueur.victor.VictorConfiguration;
import fr.traqueur.victor.entities.transaction.TransactionContext;
import fr.traqueur.victor.exceptions.VictorException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ConnectionManager {

    private static final ConcurrentMap<String, ConnectionManager> instances = new ConcurrentHashMap<>();

    private final VictorConfiguration configuration;
    private final String jdbcUrl;
    private final Properties connectionProperties;
    private volatile boolean closed = false;

    private ConnectionManager(VictorConfiguration configuration) {
        this.configuration = configuration;
        this.jdbcUrl = configuration.connectionUrl();
        this.connectionProperties = buildConnectionProperties(configuration);

        // Load driver
        loadDriver();

        // Test connection
        testConnection();
    }

    public static ConnectionManager getInstance(VictorConfiguration configuration) {
        String key = configuration.connectionUrl();
        return instances.computeIfAbsent(key, k -> new ConnectionManager(configuration));
    }

    public Connection getConnection() {
        checkNotClosed();

        Connection transactionalConnection = TransactionContext.getCurrentConnection();
        if (transactionalConnection != null) {
            return transactionalConnection;
        }

        try {
            Connection conn = DriverManager.getConnection(jdbcUrl, connectionProperties);

            conn.setAutoCommit(true);

            String[] setupSql = configuration.dialect().getConnectionSetupSql();
            if (setupSql.length > 0) {
                try (var stmt = conn.createStatement()) {
                    for (String sql : setupSql) {
                        stmt.execute(sql);
                    }
                }
            }

            return conn;

        } catch (SQLException e) {
            throw new VictorException("Failed to get database connection: " + e.getMessage(), e);
        }
    }

    private Properties buildConnectionProperties(VictorConfiguration config) {
        Properties props = new Properties();

        // Start with dialect defaults
        props.putAll(config.dialect().getDefaultConnectionProperties());

        // Add user credentials
        if (config.username() != null) {
            props.setProperty("user", config.username());
        }
        if (config.password() != null) {
            props.setProperty("password", config.password());
        }

        // Add custom properties (override defaults)
        props.putAll(config.connectionProperties());

        return props;
    }

    private void loadDriver() {
        try {
            Class.forName(configuration.dialect().getDriverClassName());
        } catch (ClassNotFoundException e) {
            throw new VictorException("Database driver not found: " + configuration.dialect().getDriverClassName() +
                    ". Make sure to add the appropriate dialect module dependency.", e);
        }
    }

    private void testConnection() {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, connectionProperties)) {
            if (configuration.showSql()) {
                System.out.println("Database connection established: " +
                        conn.getMetaData().getDatabaseProductName() +
                        " " + conn.getMetaData().getDatabaseProductVersion());
            }
        } catch (SQLException e) {
            throw new VictorException("Failed to establish database connection: " + e.getMessage(), e);
        }
    }

    public void close() {
        closed = true;
        instances.remove(configuration.connectionUrl());

        if (configuration.showSql()) {
            System.out.println("Connection manager closed for: " + configuration.connectionUrl());
        }
    }

    private void checkNotClosed() {
        if (closed) {
            throw new VictorException("Connection manager is closed");
        }
    }

    public VictorConfiguration getConfiguration() {
        return configuration;
    }

    public boolean isClosed() {
        return closed;
    }
}