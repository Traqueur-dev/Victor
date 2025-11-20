package fr.traqueur.victor.database;

import fr.traqueur.victor.VictorConfiguration;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.types.VictorDialect;

import javax.sql.DataSource;
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
        loadDriver(configuration.dialect());
        
        // Test connection
        testConnection();
    }
    
    public static ConnectionManager getInstance(VictorConfiguration configuration) {
        String key = configuration.connectionUrl();
        return instances.computeIfAbsent(key, k -> new ConnectionManager(configuration));
    }
    
    public Connection getConnection() {
        checkNotClosed();
        
        try {
            Connection conn = DriverManager.getConnection(jdbcUrl, connectionProperties);
            
            // Configure connection
            conn.setAutoCommit(true);
            
            if (configuration.dialect() == VictorDialect.SQLITE) {
                // Enable foreign keys for SQLite
                try (var stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON");
                }
            }
            
            return conn;
            
        } catch (SQLException e) {
            throw new VictorException("Failed to get database connection: " + e.getMessage(), e);
        }
    }
    
    private Properties buildConnectionProperties(VictorConfiguration config) {
        Properties props = new Properties();
        
        // Add user credentials
        if (config.username() != null) {
            props.setProperty("user", config.username());
        }
        if (config.password() != null) {
            props.setProperty("password", config.password());
        }
        
        // Add custom properties
        props.putAll(config.connectionProperties());
        
        // Add dialect-specific properties
        switch (config.dialect()) {
            case MYSQL, MARIADB -> {
                props.setProperty("useUnicode", "true");
                props.setProperty("characterEncoding", "UTF-8");
                props.setProperty("autoReconnect", "true");
                props.setProperty("failOverReadOnly", "false");
            }
            case POSTGRESQL -> {
                props.setProperty("stringtype", "unspecified");
            }
            case H2 -> {
                props.setProperty("DB_CLOSE_ON_EXIT", "FALSE");
                if (!props.containsKey("MODE")) {
                    props.setProperty("MODE", "MySQL");
                }
            }
            case SQLITE -> {
                props.setProperty("journal_mode", "WAL");
                props.setProperty("synchronous", "NORMAL");
            }
        }
        
        return props;
    }
    
    private void loadDriver(VictorDialect dialect) {
        try {
            Class.forName(dialect.getDriverClassName());
        } catch (ClassNotFoundException e) {
            throw new VictorException("Database driver not found: " + dialect.getDriverClassName() + 
                                    ". Make sure to add the appropriate JDBC driver dependency.", e);
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