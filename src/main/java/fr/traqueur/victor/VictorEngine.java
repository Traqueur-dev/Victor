package fr.traqueur.victor;

import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.exceptions.VictorException;

public final class VictorEngine {
    
    private final VictorConfiguration configuration;
    private boolean closed = false;
    
    public VictorEngine(VictorConfiguration configuration) {
        this.configuration = configuration;
        initialize();
    }
    
    private void initialize() {
        // TODO: Initialize dialect manager, connection provider, proxy factory, etc.
        System.out.println("Victor Engine initialized with dialect: " + configuration.dialect());
        if (configuration.showSql()) {
            System.out.println("SQL logging enabled");
        }
    }
    
    @SuppressWarnings("unchecked")
    public <T extends Repository<?, ?, ?>> T createRepository(Class<T> repositoryInterface) {
        checkNotClosed();
        
        // TODO: Create dynamic proxy for repository
        throw new VictorException("Repository creation not yet implemented: " + repositoryInterface.getName());
    }
    
    @SuppressWarnings("unchecked")
    public <T extends Service<?, ?, ?>> T createService(Class<T> serviceInterface) {
        checkNotClosed();
        
        // TODO: Create dynamic proxy for service
        throw new VictorException("Service creation not yet implemented: " + serviceInterface.getName());
    }
    
    public void runMigrations() {
        checkNotClosed();
        
        if (!configuration.autoMigrate()) {
            return;
        }
        
        // TODO: Implement auto-migration
        System.out.println("Auto-migration enabled, running migrations...");
    }
    
    public void close() {
        if (closed) {
            return;
        }
        
        // TODO: Close connection pool, cleanup resources
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
}