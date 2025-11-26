package fr.traqueur.victor.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.traqueur.victor.VictorConfiguration;
import fr.traqueur.victor.entities.transaction.TransactionContext;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.utils.VictorLogger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages database connection pooling using HikariCP.
 * Provides efficient connection management with automatic pooling, validation, and leak detection.
 */
public final class ConnectionManager {
    private static final ConcurrentMap<String, ConnectionManager> instances = new ConcurrentHashMap<>();

    // Default pool configuration
    private static final int DEFAULT_MIN_POOL_SIZE = 5;
    private static final int DEFAULT_MAX_POOL_SIZE = 20;
    private static final long DEFAULT_CONNECTION_TIMEOUT = 30000; // 30 seconds
    private static final long DEFAULT_IDLE_TIMEOUT = 600000; // 10 minutes
    private static final long DEFAULT_MAX_LIFETIME = 1800000; // 30 minutes

    private final VictorConfiguration configuration;
    private final HikariDataSource dataSource;
    private volatile boolean closed = false;

    private ConnectionManager(VictorConfiguration configuration) {
        this.configuration = configuration;
        this.dataSource = createDataSource(configuration);

        VictorLogger.info("Connection pool initialized for database: {}", configuration.dialect().getName());
    }

    public static ConnectionManager getInstance(VictorConfiguration configuration) {
        String key = configuration.connectionUrl();
        return instances.computeIfAbsent(key, k -> new ConnectionManager(configuration));
    }

    public Connection getConnection() {
        checkNotClosed();

        // Return transactional connection if in a transaction
        Connection transactionalConnection = TransactionContext.getCurrentConnection();
        if (transactionalConnection != null) {
            return transactionalConnection;
        }

        try {
            Connection conn = dataSource.getConnection();

            // Execute dialect-specific setup SQL
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
            VictorLogger.error("Failed to get database connection", e);
            throw new VictorException("Failed to get database connection: " + e.getMessage(), e);
        }
    }

    /**
     * Creates and configures a HikariCP data source.
     */
    private HikariDataSource createDataSource(VictorConfiguration config) {
        HikariConfig hikariConfig = new HikariConfig();

        // Basic connection settings
        hikariConfig.setJdbcUrl(config.connectionUrl());
        hikariConfig.setDriverClassName(config.dialect().getDriverClassName());

        if (config.username() != null) {
            hikariConfig.setUsername(config.username());
        }
        if (config.password() != null) {
            hikariConfig.setPassword(config.password());
        }

        // Pool sizing
        hikariConfig.setMinimumIdle(DEFAULT_MIN_POOL_SIZE);
        hikariConfig.setMaximumPoolSize(DEFAULT_MAX_POOL_SIZE);

        // Timeouts
        hikariConfig.setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT);
        hikariConfig.setIdleTimeout(DEFAULT_IDLE_TIMEOUT);
        hikariConfig.setMaxLifetime(DEFAULT_MAX_LIFETIME);

        // Performance settings
        hikariConfig.setAutoCommit(true);
        hikariConfig.setPoolName("VictorPool-" + config.dialect().getName());

        // Leak detection in development mode
        if (config.showSql()) {
            hikariConfig.setLeakDetectionThreshold(60000); // 60 seconds
        }

        // Apply custom connection properties
        config.connectionProperties().forEach((key, value) ->
            hikariConfig.addDataSourceProperty(key.toString(), value)
        );

        // Apply dialect-specific default properties
        config.dialect().getDefaultConnectionProperties().forEach((key, value) ->
            hikariConfig.addDataSourceProperty(key.toString(), value)
        );

        try {
            HikariDataSource ds = new HikariDataSource(hikariConfig);

            // Test connection
            try (Connection testConn = ds.getConnection()) {
                VictorLogger.info("Database connection pool established: {} {}",
                    testConn.getMetaData().getDatabaseProductName(),
                    testConn.getMetaData().getDatabaseProductVersion());
            }

            return ds;
        } catch (Exception e) {
            VictorLogger.error("Failed to create connection pool", e);
            throw new VictorException("Failed to create connection pool: " + e.getMessage(), e);
        }
    }

    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        instances.remove(configuration.connectionUrl());

        if (dataSource != null && !dataSource.isClosed()) {
            VictorLogger.info("Closing connection pool for: {}", configuration.dialect().getName());
            dataSource.close();
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