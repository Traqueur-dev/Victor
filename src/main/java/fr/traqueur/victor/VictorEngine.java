package fr.traqueur.victor;

import fr.traqueur.victor.database.migration.AutoMigration;
import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.proxy.RepositoryProxyHandler;
import fr.traqueur.victor.proxy.ServiceProxyHandler;
import fr.traqueur.victor.database.ConnectionManager;
import fr.traqueur.victor.database.SqlExecutor;
import fr.traqueur.victor.database.SqlGenerator;
import fr.traqueur.victor.exceptions.VictorException;

public final class VictorEngine {

    private final VictorConfiguration configuration;
    private final ConnectionManager connectionManager;
    private final SqlExecutor sqlExecutor;
    private final SqlGenerator sqlGenerator;
    private boolean closed = false;

    public VictorEngine(VictorConfiguration configuration) {
        this.configuration = configuration;
        this.connectionManager = ConnectionManager.getInstance(configuration);
        this.sqlExecutor = new SqlExecutor(connectionManager);
        this.sqlGenerator = new SqlGenerator(configuration.dialect());
        initialize();
    }

    private void initialize() {
        System.out.println("Victor Engine initialized with dialect: " + configuration.dialect());
        if (configuration.showSql()) {
            System.out.println("SQL logging enabled");
        }

        // Initialize connection manager to test connectivity
        try (var conn = connectionManager.getConnection()) {
            if (configuration.showSql()) {
                System.out.println("Database connection verified");
            }
        } catch (Exception e) {
            throw new VictorException("Failed to initialize database connection", e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Repository<?, ?, ?>> T createRepository(Class<T> repositoryInterface) {
        checkNotClosed();

        try {
            return RepositoryProxyHandler.createProxy(repositoryInterface, sqlExecutor, sqlGenerator);
        } catch (Exception e) {
            throw new VictorException("Failed to create repository proxy for: " + repositoryInterface.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Service<?, ?, ?>> T createService(Class<T> serviceInterface) {
        checkNotClosed();

        try {
            return ServiceProxyHandler.createProxy(serviceInterface);
        } catch (Exception e) {
            throw new VictorException("Failed to create service proxy for: " + serviceInterface.getName(), e);
        }
    }

    public void runMigrations() {
        checkNotClosed();

        if (!configuration.autoMigrate()) {
            return;
        }

        var autoMigration = new AutoMigration(
                configuration, connectionManager, sqlExecutor, sqlGenerator);
        autoMigration.runMigrations();
    }

    public void close() {
        if (closed) {
            return;
        }

        try {
            if (connectionManager != null) {
                connectionManager.close();
            }
        } catch (Exception e) {
            System.err.println("Error closing connection manager: " + e.getMessage());
        }

        System.out.println("Victor Engine closed");
        closed = true;
    }

    private void checkNotClosed() {
        if (closed) {
            throw new VictorException("Victor engine is closed");
        }
    }

    public VictorConfiguration getConfiguration() {
        return configuration;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public SqlExecutor getSqlExecutor() {
        return sqlExecutor;
    }

    public SqlGenerator getSqlGenerator() {
        return sqlGenerator;
    }
}