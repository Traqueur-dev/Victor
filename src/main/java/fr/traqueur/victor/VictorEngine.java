package fr.traqueur.victor;

import fr.traqueur.victor.database.migration.AutoMigration;
import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.dialect.Dialect;
import fr.traqueur.victor.managers.ConnectionManager;
import fr.traqueur.victor.database.SqlExecutor;
import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.managers.TransactionManager;
import fr.traqueur.victor.proxy.RepositoryProxyHandler;
import fr.traqueur.victor.proxy.ServiceProxyHandler;
import fr.traqueur.victor.utils.VictorLogger;

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

        // Enable debug logging if showSql is enabled
        VictorLogger.setDebugEnabled(configuration.showSql());

        // Initialize components using the dialect interface
        this.connectionManager = ConnectionManager.getInstance(configuration);
        this.sqlExecutor = new SqlExecutor(connectionManager, dialect);
        this.transactionManager = new TransactionManager(connectionManager);

        VictorLogger.debug("Victor Engine initialized with dialect: " + dialect.getName());
    }

    public <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID, T extends Repository<DTO,MODEL,ID>> T createRepository(Class<T> repositoryInterface) {
        checkNotClosed();
        return RepositoryProxyHandler.createProxy(repositoryInterface, sqlExecutor, dialect);
    }

    public <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID, REPO extends Repository<DTO,MODEL,ID>, T extends Service<MODEL, DTO, ID, REPO>> T createService(Class<T> serviceInterface) {
        checkNotClosed();
        return ServiceProxyHandler.createProxy(serviceInterface, sqlExecutor, dialect);
    }

    public void runMigrations() {
        checkNotClosed();

        if (!configuration.autoMigrate()) {
            return;
        }

        VictorLogger.info("Running auto-migration...");
        AutoMigration autoMigration = new AutoMigration(
                configuration, sqlExecutor, dialect
        );
        autoMigration.runMigrations();
        VictorLogger.info("Auto-migration completed");
    }

    public void close() {
        if (closed) {
            return;
        }

        try {
            connectionManager.close();
            VictorLogger.info("Victor Engine closed");
        } catch (Exception e) {
            VictorLogger.error("Error closing Victor Engine", e);
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