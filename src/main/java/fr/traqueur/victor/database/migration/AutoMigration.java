package fr.traqueur.victor.database.migration;

import fr.traqueur.victor.VictorConfiguration;
import fr.traqueur.victor.annotations.VictorIndex;
import fr.traqueur.victor.database.SqlExecutor;
import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.entity.dialect.Dialect;
import fr.traqueur.victor.entity.metadata.EntityMetadata;
import fr.traqueur.victor.entity.metadata.FieldMetadata;
import fr.traqueur.victor.entity.metadata.RelationshipMetadata;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.registries.EntityMetadataRegistry;
import fr.traqueur.victor.scanner.EntityScanner;
import fr.traqueur.victor.utils.VictorLogger;

import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.stream.Collectors;

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

        if (entityClasses.isEmpty()) {
            VictorLogger.warn("No E classes found. Attempting auto-scan...");
            entityClasses = EntityScanner.scanForEntities();
        }

        Set<String> existingTables = getExistingTables();
        VictorLogger.debug("Found existing tables: " + existingTables);

        // Phase 1 — Create/update scalar tables
        for (Class<?> entityClass : entityClasses) {
            try {
                EntityMetadata metadata = EntityMetadataRegistry.getInstance().getMetadata(entityClass);
                migrateTable(metadata, existingTables);
            } catch (Exception e) {
                VictorLogger.error("Failed to migrate E {}: {}", entityClass.getSimpleName(), e.getMessage());
            }
        }

        // Re-fetch tables after phase 1 (new tables may have been created)
        existingTables = getExistingTables();

        // Phase 2 — FK columns for owning-side relationships (ManyToOne / OneToOne owning)
        for (Class<?> entityClass : entityClasses) {
            try {
                EntityMetadata metadata = EntityMetadataRegistry.getInstance().getMetadata(entityClass);
                migrateForeignKeyColumns(metadata);
            } catch (Exception e) {
                VictorLogger.error("Failed to migrate FK columns for {}: {}", entityClass.getSimpleName(), e.getMessage());
            }
        }

        // Phase 3 — Junction tables (ManyToMany)
        Set<String> processedJoinTables = new HashSet<>();
        for (Class<?> entityClass : entityClasses) {
            try {
                EntityMetadata metadata = EntityMetadataRegistry.getInstance().getMetadata(entityClass);
                migrateJunctionTables(metadata, processedJoinTables);
            } catch (Exception e) {
                VictorLogger.error("Failed to migrate junction tables for {}: {}", entityClass.getSimpleName(), e.getMessage());
            }
        }

        // Phase 4 — Indexes
        for (Class<?> entityClass : entityClasses) {
            try {
                EntityMetadata metadata = EntityMetadataRegistry.getInstance().getMetadata(entityClass);
                processIndexes(metadata);
            } catch (Exception e) {
                VictorLogger.error("Failed to process indexes for {}: {}", entityClass.getSimpleName(), e.getMessage());
            }
        }
    }

    // ========== Phase 1: Table migration ==========

    private void migrateTable(EntityMetadata metadata, Set<String> existingTables) {
        String tableName = metadata.getTableName().toLowerCase();

        if (existingTables.contains(tableName)) {
            updateTableIfNeeded(metadata);
        } else {
            createTable(metadata);
            VictorLogger.info("Created table '{}'", tableName);
        }
    }

    private void createTable(EntityMetadata metadata) {
        if (metadata.getSchema() != null && dialect.supportsSchemas()) {
            String createSchemaSQL = dialect.generateCreateSchema(metadata.getSchema());
            if (createSchemaSQL != null) {
                try {
                    sqlExecutor.executeDDL(createSchemaSQL);
                    VictorLogger.info("Schema created or already exists: " + metadata.getSchema());
                } catch (Exception e) {
                    VictorLogger.error("Failed to create schema {}", metadata.getSchema(), e);
                }
            }
        }

        String createTableSql = dialect.generateCreateTable(metadata);

        try {
            sqlExecutor.executeDDL(createTableSql);
        } catch (Exception e) {
            throw new VictorException("Failed to create table " + metadata.getFullTableName(), e);
        }
    }

    // ========== ALTER TABLE — schema diff ==========

    private void updateTableIfNeeded(EntityMetadata metadata) {
        List<DatabaseColumn> existingColumns;
        try {
            existingColumns = queryExistingColumns(metadata);
        } catch (Exception e) {
            VictorLogger.error("Failed to query columns for table '{}': {}",
                    metadata.getTableName(), e.getMessage());
            return;
        }

        Set<String> existingColumnNames = existingColumns.stream()
                .map(DatabaseColumn::columnName)
                .collect(Collectors.toSet());

        // Expected columns = scalar fields + FK columns from owning-side relations
        List<FieldMetadata> expectedFields = metadata.getAllPersistableFields();
        Set<String> expectedColumnNames = expectedFields.stream()
                .map(f -> f.getColumnName().toLowerCase())
                .collect(Collectors.toSet());

        // Detect new columns
        List<FieldMetadata> columnsToAdd = expectedFields.stream()
                .filter(f -> !existingColumnNames.contains(f.getColumnName().toLowerCase()))
                .filter(f -> !f.isId())
                .toList();

        for (FieldMetadata field : columnsToAdd) {
            addColumn(metadata, field);
        }

        // Warn about removed columns
        Set<String> removedColumns = existingColumnNames.stream()
                .filter(col -> !expectedColumnNames.contains(col))
                .collect(Collectors.toSet());

        for (String removedCol : removedColumns) {
            VictorLogger.warn(
                    "Column '{}' exists in table '{}' but is not defined in the E. " +
                    "Victor will NOT drop this column automatically to prevent data loss.",
                    removedCol, metadata.getTableName()
            );
        }

        // Warn about type changes
        Map<String, DatabaseColumn> existingColumnMap = existingColumns.stream()
                .collect(Collectors.toMap(DatabaseColumn::columnName, c -> c));

        for (FieldMetadata field : expectedFields) {
            String colName = field.getColumnName().toLowerCase();
            DatabaseColumn dbCol = existingColumnMap.get(colName);
            if (dbCol != null) {
                String expectedType = field.hasCustomSqlType()
                        ? field.getSqlType()
                        : dialect.mapJavaTypeToSql(field.getJavaType(), field);
                String normalizedExpected = normalizeTypeName(expectedType);
                String normalizedActual = normalizeTypeName(dbCol.dataType());
                if (!normalizedExpected.equals(normalizedActual)) {
                    VictorLogger.warn(
                            "Column '{}' in table '{}' has type '{}' but E expects '{}'. " +
                            "Victor will NOT alter column types automatically.",
                            colName, metadata.getTableName(), dbCol.dataType(), expectedType
                    );
                }
            }
        }
    }

    private void addColumn(EntityMetadata metadata, FieldMetadata field) {
        boolean isSqlite = "sqlite".equalsIgnoreCase(dialect.getName());

        if (isSqlite && !field.isNullable() && !field.hasDefaultValue()) {
            VictorLogger.warn(
                    "SQLite cannot add NOT NULL column '{}' to table '{}' without a default value. " +
                    "The column will be added as nullable instead.",
                    field.getColumnName(), metadata.getTableName()
            );
        }

        if (isSqlite && field.isUnique()) {
            VictorLogger.warn(
                    "SQLite cannot add column '{}' with UNIQUE constraint via ALTER TABLE. " +
                    "The UNIQUE constraint will be skipped. Consider creating a UNIQUE index instead.",
                    field.getColumnName()
            );
        }

        String sql = dialect.generateAddColumn(metadata, field);

        try {
            sqlExecutor.executeDDL(sql);
            VictorLogger.info("Added column '{}' to table '{}'",
                    field.getColumnName(), metadata.getTableName());
        } catch (Exception e) {
            VictorLogger.error("Failed to add column '{}' to table '{}': {}",
                    field.getColumnName(), metadata.getTableName(), e.getMessage());
        }
    }

    // ========== Phase 2: FK columns ==========

    private void migrateForeignKeyColumns(EntityMetadata metadata) {
        for (RelationshipMetadata rel : metadata.getOwningSideRelationships()) {
            String fkColumn = rel.getForeignKeyColumn();

            List<DatabaseColumn> existingColumns;
            try {
                existingColumns = queryExistingColumns(metadata);
            } catch (Exception e) {
                continue;
            }

            boolean fkExists = existingColumns.stream()
                    .anyMatch(c -> c.columnName().equalsIgnoreCase(fkColumn));

            if (!fkExists) {
                EntityMetadata targetMeta = EntityMetadataRegistry.getInstance().getMetadata(rel.getTargetEntityClass());
                FieldMetadata fkField = FieldMetadata.forForeignKey(
                        fkColumn, targetMeta.getIdField().getJavaType(), rel.isNullable()
                );
                addColumn(metadata, fkField);
            }

            // Optionally add FK constraint (SQLiteDialect returns null)
            EntityMetadata targetMeta = EntityMetadataRegistry.getInstance().getMetadata(rel.getTargetEntityClass());
            String fkSql = dialect.generateAddForeignKeyConstraint(
                    metadata.getTableName(), fkColumn,
                    targetMeta.getTableName(), targetMeta.getIdField().getColumnName()
            );
            if (fkSql != null) {
                try {
                    sqlExecutor.executeDDL(fkSql);
                    VictorLogger.debug("FK constraint added: {} -> {}", fkColumn, targetMeta.getTableName());
                } catch (Exception e) {
                    // FK constraint may already exist or not be supported — non-fatal
                    VictorLogger.debug("FK constraint skipped for '{}': {}", fkColumn, e.getMessage());
                }
            }
        }
    }

    // ========== Phase 3: Junction tables ==========

    private void migrateJunctionTables(EntityMetadata metadata, Set<String> processedJoinTables) {
        for (RelationshipMetadata rel : metadata.getRelationships()) {
            if (rel.getType() != RelationshipMetadata.RelationType.MANY_TO_MANY) continue;

            String joinTable = rel.getJoinTable();
            if (!processedJoinTables.add(joinTable)) continue; // already created

            EntityMetadata targetMeta = EntityMetadataRegistry.getInstance().getMetadata(rel.getTargetEntityClass());

            String createSql = dialect.generateCreateJoinTable(
                    joinTable,
                    rel.getJoinColumn(), metadata.getIdField().getJavaType(),
                    rel.getInverseJoinColumn(), targetMeta.getIdField().getJavaType()
            );

            try {
                sqlExecutor.executeDDL(createSql);
                VictorLogger.info("Created junction table '{}'", joinTable);
            } catch (Exception e) {
                VictorLogger.error("Failed to create junction table '{}': {}", joinTable, e.getMessage());
            }
        }
    }

    // ========== Phase 4: Index processing ==========

    private void processIndexes(EntityMetadata metadata) {
        Class<?> entityClass = metadata.getEntityClass();

        // Class-level @VictorIndex annotations
        VictorIndex[] classIndexes = entityClass.getAnnotationsByType(VictorIndex.class);
        for (VictorIndex idx : classIndexes) {
            if (idx.columns().length == 0) {
                VictorLogger.warn("@VictorIndex on class {} has no columns defined, skipping.",
                        entityClass.getSimpleName());
                continue;
            }
            createIndex(metadata, idx.name(), idx.columns(), idx.unique(), idx.where());
        }

        // Field/component-level @VictorIndex annotations
        if (entityClass.isRecord()) {
            for (RecordComponent component : entityClass.getRecordComponents()) {
                VictorIndex[] fieldIndexes = component.getAnnotationsByType(VictorIndex.class);
                for (VictorIndex idx : fieldIndexes) {
                    // Resolve column name from FieldMetadata
                    FieldMetadata fm = metadata.getFieldByName(component.getName());
                    String columnName = fm != null ? fm.getColumnName() : component.getName();
                    String[] columns = idx.columns().length > 0 ? idx.columns() : new String[]{columnName};
                    createIndex(metadata, idx.name(), columns, idx.unique(), idx.where());
                }
            }
        } else {
            Class<?> current = entityClass;
            while (current != null && current != Object.class) {
                for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                    VictorIndex[] fieldIndexes = field.getAnnotationsByType(VictorIndex.class);
                    for (VictorIndex idx : fieldIndexes) {
                        FieldMetadata fm = metadata.getFieldByName(field.getName());
                        String columnName = fm != null ? fm.getColumnName() : field.getName();
                        String[] columns = idx.columns().length > 0 ? idx.columns() : new String[]{columnName};
                        createIndex(metadata, idx.name(), columns, idx.unique(), idx.where());
                    }
                }
                current = current.getSuperclass();
            }
        }
    }

    private void createIndex(EntityMetadata metadata, String name, String[] columns,
                             boolean unique, String where) {
        String indexName = name.isEmpty()
                ? generateIndexName(metadata.getTableName(), columns, unique)
                : name;

        String effectiveWhere = where;
        if (!effectiveWhere.isEmpty() && !dialect.supportsPartialIndexes()) {
            VictorLogger.warn("Dialect {} does not support partial indexes. " +
                            "WHERE clause will be ignored for index '{}'.",
                    dialect.getName(), indexName);
            effectiveWhere = "";
        }

        IndexDefinition indexDef = new IndexDefinition(indexName, columns, unique, effectiveWhere);
        String sql = dialect.generateCreateIndex(metadata, indexDef);

        try {
            sqlExecutor.executeDDL(sql);
            VictorLogger.debug("Index '{}' ensured on table '{}'", indexName, metadata.getTableName());
        } catch (Exception e) {
            VictorLogger.debug("Index '{}' may already exist on table '{}': {}",
                    indexName, metadata.getTableName(), e.getMessage());
        }
    }

    // ========== Column introspection ==========

    private List<DatabaseColumn> queryExistingColumns(EntityMetadata metadata) {
        String sql = dialect.generateListColumnsSQL(metadata.getTableName(), metadata.getSchema());

        if ("sqlite".equalsIgnoreCase(dialect.getName())) {
            return sqlExecutor.executeQuery(sql, null, rs -> new DatabaseColumn(
                    rs.getString("name").toLowerCase(),
                    rs.getString("type"),
                    rs.getInt("notnull") == 0,
                    rs.getString("dflt_value")
            ));
        } else {
            return sqlExecutor.executeQuery(sql, null, rs -> new DatabaseColumn(
                    rs.getString("COLUMN_NAME").toLowerCase(),
                    rs.getString("DATA_TYPE"),
                    "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                    rs.getString("COLUMN_DEFAULT")
            ));
        }
    }

    // ========== Helpers ==========

    private String generateIndexName(String tableName, String[] columns, boolean unique) {
        String prefix = unique ? "uk_" : "idx_";
        return prefix + tableName + "_" + String.join("_", columns);
    }

    private String normalizeTypeName(String typeName) {
        if (typeName == null) return "";
        String normalized = typeName.toUpperCase().trim();
        int parenIndex = normalized.indexOf('(');
        if (parenIndex > 0) {
            normalized = normalized.substring(0, parenIndex);
        }
        return switch (normalized) {
            case "INT", "INT4" -> "INTEGER";
            case "INT8", "BIGSERIAL" -> "BIGINT";
            case "SERIAL" -> "INTEGER";
            case "BOOL" -> "BOOLEAN";
            case "FLOAT4" -> "REAL";
            case "FLOAT8" -> "DOUBLE PRECISION";
            case "CHARACTER VARYING" -> "VARCHAR";
            case "TINYINT" -> "BOOLEAN";
            default -> normalized;
        };
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

        Set<String> tables = sqlExecutor.executeQueryForStringSet(sql);

        VictorLogger.debug("Found {} existing tables for schema {}", tables.size(),
                (schemaName != null ? schemaName : "default"));

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
                .collect(Collectors.toSet());

        if (schemas.size() == 1) {
            return schemas.iterator().next();
        }

        return null;
    }
}