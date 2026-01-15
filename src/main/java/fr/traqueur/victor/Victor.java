package fr.traqueur.victor;

import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.entities.transaction.Transaction;
import fr.traqueur.victor.entities.transaction.TransactionalCallable;
import fr.traqueur.victor.entities.transaction.TransactionalOperation;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.managers.TransactionManager;

public final class Victor {

    private static Victor defaultInstance;
    private final VictorEngine engine;
    private final TransactionManager transactionManager;

    private Victor(VictorEngine engine) {
        this.engine = engine;
        this.transactionManager = engine.getTransactionManager();
    }

    public static <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID, T extends Repository<DTO,MODEL,ID>> T withRepository(Class<T> repositoryClass) {
        return getDefaultOrThrow().createRepository(repositoryClass);
    }

    public static <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID, REPO extends Repository<DTO,MODEL,ID>, T extends Service<MODEL, DTO, ID, REPO>> T withService(Class<T> serviceClass) {
        return getDefaultOrThrow().createService(serviceClass);
    }

    public Transaction beginTransaction() {
        return transactionManager.beginTransaction();
    }

    public void transaction(TransactionalOperation operation) {
        transactionManager.executeInTransaction(operation);
    }

    public <T> T transaction(TransactionalCallable<T> callable) {
        return transactionManager.executeInTransaction(callable);
    }

    public static void withTransaction(TransactionalOperation operation) {
        getDefaultOrThrow().transaction(operation);
    }

    public static <T> T withTransaction(TransactionalCallable<T> callable) {
        return getDefaultOrThrow().transaction(callable);
    }

    public static void connect(String jdbcUrl) {
        setDefault(configure()
                .url(jdbcUrl)
                .autoDetectDialect()
                .autoMigrate()
                .build());
    }

    public static VictorBuilder configure() {
        return new VictorBuilder();
    }

    public static void autoConnect(String jdbcUrl) {
        setDefault(configure()
                .url(jdbcUrl)
                .autoDetectDialect()
                .autoMigrate()
                .autoScanEntities()
                .build());
    }

    public static void autoConnectPackages(String jdbcUrl, String... packages) {
        setDefault(configure()
                .url(jdbcUrl)
                .autoDetectDialect()
                .autoMigrate()
                .autoScanEntities(packages)
                .build());
    }

    public static Victor sqlite(String path) {
        return configure()
                .sqlite()
                .file(path)
                .autoMigrate()
                .build();
    }

    public static Victor mysql(String host, String database, String user, String password) {
        return configure()
                .mysql()
                .host(host)
                .database(database)
                .credentials(user, password)
                .autoMigrate()
                .build();
    }

    public static Victor postgresql(String host, String database, String user, String password) {
        return configure()
                .postgresql()
                .host(host)
                .database(database)
                .credentials(user, password)
                .autoMigrate()
                .build();
    }

    public static Victor h2(String database) {
        return configure()
                .h2()
                .database(database)
                .autoMigrate()
                .build();
    }

    public <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID, T extends Repository<DTO,MODEL,ID>> T createRepository(Class<T> repositoryClass) {
        return engine.createRepository(repositoryClass);
    }

    public <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID, REPO extends Repository<DTO,MODEL,ID>, T extends Service<MODEL, DTO, ID, REPO>> T createService(Class<T> serviceClass) {
        return engine.createService(serviceClass);
    }

    public VictorConfiguration getConfiguration() {
        return engine.getConfiguration();
    }

    public void close() {
        try {
            engine.close();
            if (defaultInstance == this) {
                defaultInstance = null;
            }
        } catch (Exception e) {
            throw new VictorException("Failed to close Victor", e);
        }
    }

    public static Victor getDefault() {
        return defaultInstance;
    }

    public static Victor getDefaultOrThrow() {
        if (defaultInstance == null) {
            throw new VictorException("Victor not configured! Call Victor.connect() or Victor.configure() first.");
        }
        return defaultInstance;
    }

    public static void setDefault(Victor victor) {
        if (defaultInstance != null && defaultInstance != victor) {
            defaultInstance.close();
        }
        defaultInstance = victor;
    }

    static Victor create(VictorEngine engine) {
        return new Victor(engine);
    }
}