package fr.traqueur.victor;

import fr.traqueur.victor.database.migration.AutoMigration;
import fr.traqueur.victor.entities.dialect.Dialect;
import fr.traqueur.victor.managers.ConnectionManager;
import fr.traqueur.victor.database.SqlExecutor;
import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.managers.TransactionManager;
import fr.traqueur.victor.proxy.RepositoryProxyHandler;
import fr.traqueur.victor.proxy.ServiceProxyHandler;

public final class VictorEngine {

    private final VictorConfiguration configuration;
    private final Dialect dialect;
    private final ConnectionManager connectionManager;
    private final TransactionManager transactionManager;
    private final SqlExecutor sqlExecutor;
    private boolean closed = false;

    public VictorEngine(VictorConfiguration configuration) {
        this.configuration = configuration;
        this.dialect = configuration.dialect();

        // Initialize components using the dialect interface
        this.connectionManager = ConnectionManager.getInstance(configuration);
        this.sqlExecutor = new SqlExecutor(connectionManager, dialect);
        this.transactionManager = new TransactionManager(connectionManager);

        System.out.println("Victor Engine initialized with dialect: " + dialect.getName());
        if (configuration.showSql()) {
            System.out.println("SQL logging enabled");
        }
    }

    public <T extends Repository<?, ?, ?>> T createRepository(Class<T> repositoryInterface) {
        checkNotClosed();
        return RepositoryProxyHandler.createProxy(repositoryInterface, sqlExecutor, dialect);
    }

    public <T extends Service<?, ?, ?, ?>> T createService(Class<T> serviceInterface) {
        checkNotClosed();
        return ServiceProxyHandler.createProxy(serviceInterface, sqlExecutor, dialect);
    }

    public void runMigrations() {
        checkNotClosed();

        if (!configuration.autoMigrate()) {
            return;
        }

        System.out.println("Running auto-migration...");
        AutoMigration autoMigration = new AutoMigration(
                configuration, sqlExecutor, dialect
        );
        autoMigration.runMigrations();
        System.out.println("Auto-migration completed");
    }

    public void close() {
        if (closed) {
            return;
        }

        try {
            connectionManager.close();
            System.out.println("Victor Engine closed");
        } catch (Exception e) {
            System.err.println("Error closing Victor Engine: " + e.getMessage());
        } finally {
            closed = true;
        }
    }

    private void checkNotClosed() {
        if (closed) {
            throw new VictorException("Victor engine is closed");
        }
    }

    public VictorConfiguration getConfiguration() {
        return configuration;
    }

    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    public Dialect getDialect() {
        return dialect;
    }
}