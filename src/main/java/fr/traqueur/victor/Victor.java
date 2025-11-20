package fr.traqueur.victor;

import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.exceptions.VictorException;

public final class Victor {

    private static Victor defaultInstance;
    private final VictorEngine engine;

    private Victor(VictorEngine engine) {
        this.engine = engine;
    }

    public static <T> T with(Class<T> interfaceClass) {
        return getDefaultOrThrow().create(interfaceClass);
    }

    public static <T extends Repository<?, ?, ?>> T withRepository(Class<T> repositoryClass) {
        return getDefaultOrThrow().createRepository(repositoryClass);
    }

    public static <T extends Service<?, ?, ?>> T withService(Class<T> serviceClass) {
        return getDefaultOrThrow().createService(serviceClass);
    }

    public static void connect(String jdbcUrl) {
        if (defaultInstance != null) {
            defaultInstance.close();
        }
        defaultInstance = configure()
                .url(jdbcUrl)
                .autoDetectDialect()
                .autoMigrate()
                .build();
    }

    public static VictorBuilder configure() {
        return new VictorBuilder();
    }

    public static void autoConnect(String jdbcUrl) {
        if (defaultInstance != null) {
            defaultInstance.close();
        }
        defaultInstance = configure()
                .url(jdbcUrl)
                .autoDetectDialect()
                .autoMigrate()
                .autoScanEntities()
                .build();
    }

    public static void autoConnectPackages(String jdbcUrl, String... packages) {
        if (defaultInstance != null) {
            defaultInstance.close();
        }
        defaultInstance = configure()
                .url(jdbcUrl)
                .autoDetectDialect()
                .autoMigrate()
                .autoScanEntities(packages)
                .build();
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

    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> interfaceClass) {
        if (isRepositoryInterface(interfaceClass)) {
            return (T) createRepository((Class<? extends Repository<?, ?, ?>>) interfaceClass);
        } else if (isServiceInterface(interfaceClass)) {
            return (T) createService((Class<? extends Service<?, ?, ?>>) interfaceClass);
        }

        throw new VictorException("Unsupported interface type: " + interfaceClass.getName());
    }

    public <T extends Repository<?, ?, ?>> T createRepository(Class<T> repositoryClass) {
        return engine.createRepository(repositoryClass);
    }

    public <T extends Service<?, ?, ?>> T createService(Class<T> serviceClass) {
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
        defaultInstance = victor;
    }

    private boolean isRepositoryInterface(Class<?> clazz) {
        return Repository.class.isAssignableFrom(clazz);
    }

    private boolean isServiceInterface(Class<?> clazz) {
        return Service.class.isAssignableFrom(clazz);
    }

    static Victor create(VictorEngine engine) {
        return new Victor(engine);
    }

    public void runMigrations() {
        engine.runMigrations();
    }
}