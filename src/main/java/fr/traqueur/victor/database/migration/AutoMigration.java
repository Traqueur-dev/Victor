package fr.traqueur.victor.database.migration;

import fr.traqueur.victor.VictorConfiguration;
import fr.traqueur.victor.database.SqlExecutor;
import fr.traqueur.victor.entities.dialect.Dialect;
import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.registries.EntityMetadataRegistry;
import fr.traqueur.victor.scanner.EntityScanner;

import java.util.Objects;
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
            if (configuration.showSql()) {
                System.out.println("Table already exists: " + tableName);
            }
            return;
        }

        createTable(metadata);

        if (configuration.showSql()) {
            System.out.println("✓ Created table: " + tableName);
        }
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
        try {
            return queryExistingTables();
        } catch (Exception e) {
            System.err.println("Warning: Could not query existing tables: " + e.getMessage());
            if (configuration.showSql()) {
                e.printStackTrace();
            }
            return Set.of();
        }
    }

    private Set<String> queryExistingTables() {
        String schemaName = determineSchemaForTableListing();
        String sql = dialect.generateListTablesSQL(schemaName);

        if (configuration.showSql()) {
            System.out.println("Querying existing tables with schema: " +
                    (schemaName != null ? schemaName : "default"));
        }

        Set<String> tables = sqlExecutor.executeQueryForStringSet(sql);

        if (configuration.showSql()) {
            System.out.println("Found " + tables.size() + " existing tables: " + tables);
        }

        return tables;
    }

    private String determineSchemaForTableListing() {
        var schemas = configuration.entityClasses().stream()
                .map(clazz -> {
                    try {
                        var metadata = EntityMetadataRegistry.getInstance().getMetadata(clazz);
                        return metadata.getSchema();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        if (schemas.size() == 1) {
            return schemas.iterator().next();
        }

        return null;
    }
}