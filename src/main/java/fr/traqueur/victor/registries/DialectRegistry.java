package fr.traqueur.victor.registries;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.entities.dialect.Dialect;
import fr.traqueur.victor.exceptions.VictorConfigurationException;
import fr.traqueur.victor.utils.VictorLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DialectRegistry {
    
    private static final DialectRegistry INSTANCE = new DialectRegistry();
    private final ConcurrentMap<String, Dialect> dialectsByName = new ConcurrentHashMap<>();
    private final List<Dialect> allDialects = new ArrayList<>();
    private volatile boolean initialized = false;
    
    private DialectRegistry() {}
    
    public static DialectRegistry getInstance() {
        INSTANCE.ensureInitialized();
        return INSTANCE;
    }
    
    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    loadDialects();
                    initialized = true;
                }
            }
        }
    }
    
    private void loadDialects() {
        ServiceLoader<Dialect> loader = ServiceLoader.load(Dialect.class);
        
        for (Dialect dialect : loader) {
            registerDialect(dialect);
            VictorLogger.debug("Loaded dialect {}", dialect.getClass().getSimpleName());
        }
        
        if (allDialects.isEmpty()) {
            throw new VictorConfigurationException(
                "No dialect implementations found! Make sure to include at least one dialect module " +
                "(e.g., victor-dialect-h2, victor-dialect-sqlite, etc.) in your classpath."
            );
        }

        VictorLogger.info("Loaded {} dialects", allDialects.size());
    }
    
    public void registerDialect(Dialect dialect) {
        allDialects.add(dialect);
        dialectsByName.put(dialect.getName().toLowerCase(), dialect);
        
        // Also register by common aliases
        String name = dialect.getName().toLowerCase();
        switch (name) {
            case "h2" -> dialectsByName.put("h2database", dialect);
            case "mysql" -> {
                dialectsByName.put("mariadb", dialect); // MariaDB is MySQL-compatible
                dialectsByName.put("mysql8", dialect);
            }
            case "postgresql" -> {
                dialectsByName.put("postgres", dialect);
                dialectsByName.put("pgsql", dialect);
            }
            case "sqlite" -> dialectsByName.put("sqlite3", dialect);
        }
    }
    
    /**
     * Find dialect by name (case-insensitive)
     */
    public Dialect getByName(String name) {
        Dialect dialect = dialectsByName.get(name.toLowerCase());
        if (dialect == null) {
            throw new VictorConfigurationException("Unknown dialect: " + name + 
                ". Available dialects: " + dialectsByName.keySet());
        }
        return dialect;
    }
    
    /**
     * Auto-detect dialect from JDBC URL
     */
    public Dialect detectFromUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
            throw new VictorConfigurationException("Invalid JDBC URL: " + jdbcUrl);
        }
        
        for (Dialect dialect : allDialects) {
            if (dialect.supportsUrl(jdbcUrl)) {
                return dialect;
            }
        }
        
        throw new VictorConfigurationException(
            "No dialect found for JDBC URL: " + jdbcUrl + 
            ". Make sure the appropriate dialect module is in your classpath."
        );
    }
    
    /**
     * Get all registered dialects
     */
    public List<Dialect> getAllDialects() {
        return new ArrayList<>(allDialects);
    }
    
    /**
     * Check if a dialect is available
     */
    public boolean isAvailable(String name) {
        return dialectsByName.containsKey(name.toLowerCase());
    }
    
    /**
     * Clear registry (for testing)
     */
    public void clear() {
        allDialects.clear();
        dialectsByName.clear();
        initialized = false;
    }

    public Set<String> getAvailableDialects() {
        return dialectsByName.keySet();
    }
}