package fr.traqueur.victor.database.migration;

import fr.traqueur.victor.VictorConfiguration;
import fr.traqueur.victor.database.SqlExecutor;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.dialect.Dialect;
import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.registries.EntityMetadataRegistry;
import fr.traqueur.victor.scanner.EntityScanner;
import fr.traqueur.victor.utils.VictorLogger;

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

        Set<Class<? extends Entity<?>>> entityClasses = configuration.entityClasses();

        // Si pas d'entités configurées, essayer l'auto-scan
        if (entityClasses.isEmpty()) {
            VictorLogger.warn("No entity classes found. attempting auto-scan...");
            entityClasses = EntityScanner.scanForEntities();
        }

        // Get existing tables
        Set<String> existingTables = getExistingTables();
        VictorLogger.debug("Found existing tables: " + existingTables);

        // Create tables for each entity
        for (Class<?> entityClass : entityClasses) {
            try {
                EntityMetadata metadata = EntityMetadataRegistry.getInstance().getMetadata(entityClass);
                createTableIfNotExists(metadata, existingTables);
            } catch (Exception e) {
                VictorLogger.error("Failed to migrate entity {}", e, entityClass.getSimpleName());
            }
        }
    }

    private void createTableIfNotExists(EntityMetadata metadata, Set<String> existingTables) {
        String tableName = metadata.getTableName().toLowerCase();

        if (existingTables.contains(tableName)) {
            if (configuration.showSql()) {
                VictorLogger.debug("Skipping auto-scan for table {}", tableName);
            }
            return;
        }

        createTable(metadata);

        if (configuration.showSql()) {
            VictorLogger.debug("Auto-scan for table {}", tableName);
        }
    }

    private void createTable(EntityMetadata metadata) {
        // Create schema first if it doesn't exist and schema is specified
        if (metadata.getSchema() != null && dialect.supportsSchemas()) {
            String createSchemaSQL = dialect.generateCreateSchema(metadata.getSchema());
            if (createSchemaSQL != null) {
                try {
                    sqlExecutor.executeDDL(createSchemaSQL);
                    VictorLogger.info("Schema created or already exists: " + metadata.getSchema());
                } catch (Exception e) {
                    VictorLogger.error("Failed to create schema {}", e, metadata.getSchema());
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
            VictorLogger.error("Failed to query existing tables", e);
            return Set.of();
        }
    }

    private Set<String> queryExistingTables() {
        String schemaName = determineSchemaForTableListing();
        String sql = dialect.generateListTablesSQL(schemaName);

        VictorLogger.debug("Querying existing tables for schema {}", (schemaName != null ? schemaName : "default"));

        Set<String> tables = sqlExecutor.executeQueryForStringSet(sql);

        VictorLogger.info("Found {} existing tables for schema {}", tables.size(), schemaName);

        return tables;
    }

    private String determineSchemaForTableListing() {
        var schemas = configuration.entityClasses().stream()
                .map(clazz -> {
                    try {
                        EntityMetadata metadata = EntityMetadataRegistry.getInstance().getMetadata(clazz);
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