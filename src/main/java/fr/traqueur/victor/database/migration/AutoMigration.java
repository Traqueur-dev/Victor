package fr.traqueur.victor.database.migration;

import fr.traqueur.victor.VictorConfiguration;
import fr.traqueur.victor.database.ConnectionManager;
import fr.traqueur.victor.database.SqlExecutor;
import fr.traqueur.victor.database.SqlGenerator;
import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.registries.EntityMetadataRegistry;
import fr.traqueur.victor.exceptions.VictorException;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

public final class AutoMigration {

    private final VictorConfiguration configuration;
    private final ConnectionManager connectionManager;
    private final SqlExecutor sqlExecutor;
    private final SqlGenerator sqlGenerator;

    public AutoMigration(VictorConfiguration configuration, ConnectionManager connectionManager,
                         SqlExecutor sqlExecutor, SqlGenerator sqlGenerator) {
        this.configuration = configuration;
        this.connectionManager = connectionManager;
        this.sqlExecutor = sqlExecutor;
        this.sqlGenerator = sqlGenerator;
    }

    public void runMigrations() {
        if (!configuration.autoMigrate()) {
            return;
        }

        System.out.println("Running auto-migration...");

        try {
            Set<String> existingTables = getExistingTables();
            Set<Class<?>> entityClasses = configuration.entityClasses();

            System.out.println("Entity classes found: " + entityClasses.size());
            for (Class<?> clazz : entityClasses) {
                System.out.println("  - " + clazz.getSimpleName());
            }

            if (entityClasses.isEmpty()) {
                System.out.println("WARNING: No entity classes specified for migration");
                return;
            }

            for (Class<?> entityClass : entityClasses) {
                try {
                    System.out.println("Processing entity: " + entityClass.getSimpleName());
                    EntityMetadata metadata = EntityMetadataRegistry.getInstance().getMetadata(entityClass);
                    String tableName = metadata.getTableName().toLowerCase();

                    System.out.println("  Table name: " + tableName);
                    System.out.println("  Full table name: " + metadata.getFullTableName());

                    if (!existingTables.contains(tableName)) {
                        System.out.println("  Creating table...");
                        createTable(metadata);
                        System.out.println("  ✓ Created table: " + metadata.getFullTableName());
                    } else {
                        System.out.println("  Table already exists: " + metadata.getFullTableName());
                    }
                } catch (Exception e) {
                    System.err.println("Failed to migrate entity " + entityClass.getSimpleName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            System.out.println("Auto-migration completed");

        } catch (Exception e) {
            throw new VictorException("Auto-migration failed", e);
        }
    }

    private Set<String> getExistingTables() throws Exception {
        Set<String> tables = new HashSet<>();

        try (var conn = connectionManager.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Get tables for the current schema/catalog
            try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME").toLowerCase();
                    tables.add(tableName);
                }
            }
        }

        if (configuration.showSql()) {
            System.out.println("Existing tables: " + tables);
        }

        return tables;
    }

    private void createTable(EntityMetadata metadata) {
        // Create schema first if it doesn't exist and schema is specified
        if (metadata.getSchema() != null && configuration.dialect().supportsSchemas()) {
            String createSchemaSQL = "CREATE SCHEMA IF NOT EXISTS " + metadata.getSchema();
            try {
                sqlExecutor.executeDDL(createSchemaSQL);
                System.out.println("Schema created or already exists: " + metadata.getSchema());
            } catch (Exception e) {
                System.out.println("Warning: Could not create schema " + metadata.getSchema() + ": " + e.getMessage());
            }
        }

        String createTableSql = sqlGenerator.generateCreateTable(metadata);

        try {
            sqlExecutor.executeDDL(createTableSql);
        } catch (Exception e) {
            throw new VictorException("Failed to create table " + metadata.getFullTableName(), e);
        }
    }
}