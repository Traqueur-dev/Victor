package fr.traqueur.victor.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.traqueur.victor.VictorConfiguration;
import fr.traqueur.victor.entity.transaction.TransactionContext;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.utils.VictorLogger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

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

    // Pool-tuning keys recognized from connection properties (.property(...)). These are
    // HikariCP settings, so they are applied via the HikariConfig setters below and NOT
    // forwarded to the driver as DataSource properties.
    private static final Set<String> POOL_SETTING_KEYS = Set.of(
            "minimumIdle", "maximumPoolSize", "connectionTimeout", "idleTimeout", "maxLifetime");

    private final VictorConfiguration configuration;
    private final HikariDataSource dataSource;
    private final AtomicInteger refCount = new AtomicInteger(1);
    private final ThreadLocal<Connection> activeConnection = new ThreadLocal<>();
    private volatile boolean closed = false;

    private ConnectionManager(VictorConfiguration configuration) {
        this.configuration = configuration;
        this.dataSource = createDataSource(configuration);

        VictorLogger.info("Connection pool initialized for database: {}", configuration.dialect().getName());
    }

    public static ConnectionManager getInstance(VictorConfiguration configuration) {
        String key = configuration.connectionUrl();
        return instances.compute(key, (k, existing) -> {
            if (existing != null && !existing.isClosed()) {
                existing.refCount.incrementAndGet();
                VictorLogger.debug("Reusing existing connection pool, ref count: {}", existing.refCount.get());
                return existing;
            }
            return new ConnectionManager(configuration);
        });
    }

    public Connection getConnection() {
        checkNotClosed();

        // Return transactional connection if in a transaction
        Connection transactionalConnection = TransactionContext.getCurrentConnection();
        if (transactionalConnection != null) {
            return transactionalConnection;
        }

        // Reuse the connection bound to the current eager-mapping scope, if any, so a
        // nested relationship load runs on the same physical connection instead of
        // pulling a second one from the pool (see beginSharedConnection).
        Connection sharedConnection = activeConnection.get();
        if (sharedConnection != null) {
            return sharedConnection;
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
     * Binds {@code conn} as the thread's connection for the current eager entity-graph
     * mapping. While a scope is active, every {@link #getConnection()} on this thread
     * returns {@code conn}, so nested relationship loads reuse a single physical
     * connection instead of each checking out another from the pool — which, with a
     * multi-level EAGER graph under concurrency, otherwise self-deadlocks the pool.
     *
     * <p>Only the outermost read establishes the scope. Returns {@code true} when this
     * call established it (and is therefore responsible for calling
     * {@link #endSharedConnection()} and closing the connection); {@code false} when a
     * transaction or an outer mapping scope already owns the connection.</p>
     */
    public boolean beginSharedConnection(Connection conn) {
        if (TransactionContext.getCurrentConnection() != null) {
            return false;
        }
        if (activeConnection.get() != null) {
            return false;
        }
        activeConnection.set(conn);
        return true;
    }

    /**
     * Ends the eager-mapping scope opened by {@link #beginSharedConnection(Connection)}.
     * Must be called only by the owner (the call that received {@code true}).
     */
    public void endSharedConnection() {
        activeConnection.remove();
    }

    /**
     * Whether {@code connection} is owned by an outer scope — an active transaction or the
     * current eager-mapping scope — and therefore must not be closed by an inner operation.
     */
    public boolean isManagedExternally(Connection connection) {
        return connection != null
                && (connection == TransactionContext.getCurrentConnection()
                    || connection == activeConnection.get());
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

        // Custom connection properties (from VictorBuilder.property(...)). Note: the
        // accessor wraps the values as Properties defaults, so iterate stringPropertyNames()
        // (which includes defaults) — a plain forEach would see no entries.
        Properties props = config.connectionProperties();

        // Pool sizing — overridable via connection properties, e.g.
        // .property("maximumPoolSize", "50"); falls back to the defaults otherwise.
        hikariConfig.setMinimumIdle(intProperty(props, "minimumIdle", DEFAULT_MIN_POOL_SIZE));
        hikariConfig.setMaximumPoolSize(intProperty(props, "maximumPoolSize", DEFAULT_MAX_POOL_SIZE));

        // Timeouts — overridable via connection properties.
        hikariConfig.setConnectionTimeout(longProperty(props, "connectionTimeout", DEFAULT_CONNECTION_TIMEOUT));
        hikariConfig.setIdleTimeout(longProperty(props, "idleTimeout", DEFAULT_IDLE_TIMEOUT));
        hikariConfig.setMaxLifetime(longProperty(props, "maxLifetime", DEFAULT_MAX_LIFETIME));

        // Performance settings
        hikariConfig.setAutoCommit(true);
        hikariConfig.setPoolName("VictorPool-" + config.dialect().getName());

        // Leak detection in development mode
        if (config.showSql()) {
            hikariConfig.setLeakDetectionThreshold(60000); // 60 seconds
        }

        // Forward the remaining custom properties to the driver as DataSource properties
        // (the pool-tuning keys above are Hikari settings, not driver properties).
        for (String name : props.stringPropertyNames()) {
            if (!POOL_SETTING_KEYS.contains(name)) {
                hikariConfig.addDataSourceProperty(name, props.getProperty(name));
            }
        }

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

        int remaining = refCount.decrementAndGet();
        VictorLogger.debug("Connection pool close requested, remaining refs: {}", remaining);

        if (remaining > 0) {
            return; // Other consumers still using this connection pool
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

    private static int intProperty(Properties props, String key, int defaultValue) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            VictorLogger.warn("Invalid integer for property '{}' ('{}'), using default {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    private static long longProperty(Properties props, String key, long defaultValue) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            VictorLogger.warn("Invalid long for property '{}' ('{}'), using default {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    public VictorConfiguration getConfiguration() {
        return configuration;
    }

    public boolean isClosed() {
        return closed;
    }
}