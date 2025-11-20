package fr.traqueur.victor.database.migration;

import fr.traqueur.victor.VictorConfiguration;
import fr.traqueur.victor.database.SqlExecutor;
import fr.traqueur.victor.entities.dialect.Dialect;
import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.registries.EntityMetadataRegistry;
import fr.traqueur.victor.scanner.EntityScanner;

import java.util.Set;

public final class AutoMigration {

    private final VictorConfiguration configuration;
    private final SqlExecutor sqlExecutor;
    private final Dialect dialect;

    public AutoMigration(VictorConfiguration configuration, SqlExecutor sqlExecutor, Dialect dialect) {
        this.configuration = configuration;
        this.sqlExecutor = sqlExecutor;
        this.dialect = dialect;
    }

    public void runMigrations() {
        if (!configuration.autoMigrate()) {
            return;
        }

        Set<Class<?>> entityClasses = configuration.entityClasses();

        // Si pas d'entités configurées, essayer l'auto-scan
        if (entityClasses.isEmpty()) {
            System.out.println("No entities configured, attempting auto-scan...");
            entityClasses = EntityScanner.scanForEntities();
        }

        // Get existing tables
        Set<String> existingTables = getExistingTables();
        System.out.println("Existing tables: " + existingTables);

        // Create tables for each entity
        for (Class<?> entityClass : entityClasses) {
            try {
                EntityMetadata metadata = EntityMetadataRegistry.getInstance().getMetadata(entityClass);
                createTableIfNotExists(metadata, existingTables);
            } catch (Exception e) {
                System.err.println("Failed to migrate entity " + entityClass.getSimpleName() + ": " + e.getMessage());
                // Continue with other entities instead of failing completely
            }
        }
    }

    private void createTableIfNotExists(EntityMetadata metadata, Set<String> existingTables) {
        String tableName = metadata.getTableName().toLowerCase();

        if (existingTables.contains(tableName)) {
            System.out.println("Table already exists: " + tableName);
            return;
        }

        createTable(metadata);
        System.out.println("Created table: " + tableName);
    }

    private void createTable(EntityMetadata metadata) {
        // Create schema first if it doesn't exist and schema is specified
        if (metadata.getSchema() != null && dialect.supportsSchemas()) {
            String createSchemaSQL = dialect.generateCreateSchema(metadata.getSchema());
            if (createSchemaSQL != null) {
                try {
                    sqlExecutor.executeDDL(createSchemaSQL);
                    System.out.println("Schema created or already exists: " + metadata.getSchema());
                } catch (Exception e) {
                    System.out.println("Warning: Could not create schema " + metadata.getSchema() + ": " + e.getMessage());
                }
            }
        }

        // Generate CREATE TABLE SQL using the dialect
        String createTableSql = dialect.generateCreateTable(metadata);

        try {
            sqlExecutor.executeDDL(createTableSql);
        } catch (Exception e) {
            throw new VictorException("Failed to create table " + metadata.getFullTableName(), e);
        }
    }

    private Set<String> getExistingTables() {
        // Cette méthode devrait être implémentée selon le dialecte
        // Pour l'instant, on retourne un set vide pour simplifier
        try {
            return queryExistingTables();
        } catch (Exception e) {
            System.out.println("Warning: Could not query existing tables: " + e.getMessage());
            return Set.of();
        }
    }

    private Set<String> queryExistingTables() {
        // Cette logique devrait être déplacée dans chaque dialecte
        // Car chaque SGBD a sa propre façon de lister les tables

        String sql = getListTablesSQL();
        if (sql == null) {
            return Set.of();
        }

        try {
            // Implementation simplifiée - devrait utiliser SqlExecutor avec une méthode dédiée
            return Set.of(); // Pour l'instant
        } catch (Exception e) {
            return Set.of();
        }
    }

    private String getListTablesSQL() {
        // Cette méthode devrait être dans l'interface Dialect
        return switch (dialect.getName().toLowerCase()) {
            case "h2" -> "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'TABLE'";
            case "sqlite" -> "SELECT name FROM sqlite_master WHERE type='table'";
            case "mysql", "mariadb" -> "SHOW TABLES";
            case "postgresql" -> "SELECT tablename FROM pg_tables WHERE schemaname = 'public'";
            default -> null;
        };
    }
}