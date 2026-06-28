package fr.traqueur.victor.entity.dialect;

import fr.traqueur.victor.database.migration.IndexDefinition;
import fr.traqueur.victor.entity.metadata.EntityMetadata;
import fr.traqueur.victor.entity.metadata.FieldMetadata;

import java.util.Properties;
import java.util.stream.Collectors;

public interface Dialect {

    String getName();

    String getDriverClassName();

    boolean supportsUrl(String jdbcUrl);

    String buildConnectionUrl(String host, int port, String database, Properties properties);

    Properties getDefaultConnectionProperties();

    String generateCreateTable(EntityMetadata metadata);

    String generateUpsert(EntityMetadata metadata);

    String mapJavaTypeToSql(Class<?> javaType, FieldMetadata fieldMetadata);

    String quoteIdentifier(String identifier);

    boolean supportsSchemas();

    boolean supportsJsonType();

    boolean isEmbedded();

    /**
     * Whether the dialect has a native, strictly-typed UUID column type that must be bound as a
     * {@link java.util.UUID} object rather than a String (e.g. PostgreSQL). Dialects that store
     * UUIDs as text bind them as strings.
     */
    default boolean nativeUuidType() {
        return false;
    }

    String getAutoIncrementSyntax(Class<?> idType);

    String generateListTablesSQL(String schemaName);

    default String getFullTableName(EntityMetadata metadata) {
        if (supportsSchemas() && metadata.getSchema() != null) {
            return quoteIdentifier(metadata.getSchema()) + "." + quoteIdentifier(metadata.getTableName());
        }
        return quoteIdentifier(metadata.getTableName());
    }

    default String generateInsert(EntityMetadata metadata) {
        var nonIdFields = metadata.getNonIdFields();
        String columns = nonIdFields.stream()
                .map(f -> quoteIdentifier(f.getColumnName()))
                .collect(Collectors.joining(", "));
        String placeholders = nonIdFields.stream()
                .map(f -> "?")
                .collect(Collectors.joining(", "));
        return String.format("INSERT INTO %s (%s) VALUES (%s)",
                getFullTableName(metadata), columns, placeholders);
    }

    default String generateUpdate(EntityMetadata metadata) {
        var nonIdFields = metadata.getNonIdFields();
        String setClause = nonIdFields.stream()
                .map(f -> quoteIdentifier(f.getColumnName()) + " = ?")
                .collect(Collectors.joining(", "));
        return String.format("UPDATE %s SET %s WHERE %s = ?",
                getFullTableName(metadata), setClause, quoteIdentifier(metadata.getIdField().getColumnName()));
    }

    default String generateDelete(EntityMetadata metadata) {
        return String.format("DELETE FROM %s WHERE %s = ?",
                getFullTableName(metadata), quoteIdentifier(metadata.getIdField().getColumnName()));
    }

    default String generateSelectById(EntityMetadata metadata) {
        return String.format("SELECT * FROM %s WHERE %s = ?",
                getFullTableName(metadata), quoteIdentifier(metadata.getIdField().getColumnName()));
    }

    default String generateSelectAll(EntityMetadata metadata) {
        return String.format("SELECT * FROM %s", getFullTableName(metadata));
    }

    default String generateCount(EntityMetadata metadata) {
        return String.format("SELECT COUNT(*) FROM %s", getFullTableName(metadata));
    }

    default String generateExists(EntityMetadata metadata) {
        return String.format("SELECT 1 FROM %s WHERE %s = ? LIMIT 1",
                getFullTableName(metadata), quoteIdentifier(metadata.getIdField().getColumnName()));
    }

    default String escapeLikePattern(String pattern) {
        return pattern.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    default String generateCreateSchema(String schemaName) {
        if (supportsSchemas()) {
            return "CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(schemaName);
        }
        return null;
    }

    default String[] getConnectionSetupSql() {
        return new String[0];
    }

    default boolean supportsStandardGeneratedKeys() {
        return true;
    }

    default String getLastInsertIdSql() {
        return null;
    }

    /**
     * Generates SQL to list columns of an existing table.
     */
    String generateListColumnsSQL(String tableName, String schemaName);

    /**
     * Generates ALTER TABLE ADD COLUMN DDL for a single field.
     */
    String generateAddColumn(EntityMetadata metadata, FieldMetadata field);

    /**
     * Generates CREATE INDEX DDL. Default implementation uses IF NOT EXISTS syntax.
     */
    default String generateCreateIndex(EntityMetadata metadata, IndexDefinition index) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE ");
        if (index.unique()) {
            sql.append("UNIQUE ");
        }
        sql.append("INDEX IF NOT EXISTS ");
        sql.append(quoteIdentifier(index.name()));
        sql.append(" ON ");
        sql.append(getFullTableName(metadata));
        sql.append(" (");
        for (int i = 0; i < index.columns().length; i++) {
            if (i > 0) sql.append(", ");
            sql.append(quoteIdentifier(index.columns()[i]));
        }
        sql.append(")");
        if (supportsPartialIndexes() && !index.where().isEmpty()) {
            sql.append(" WHERE ").append(index.where());
        }
        return sql.toString();
    }

    /**
     * Generates CREATE TABLE for a ManyToMany junction table.
     */
    default String generateCreateJoinTable(String joinTable, String leftColumn, Class<?> leftIdType,
                                           String rightColumn, Class<?> rightIdType) {
        return String.format(
            "CREATE TABLE IF NOT EXISTS %s (%s %s NOT NULL, %s %s NOT NULL, PRIMARY KEY (%s, %s))",
            quoteIdentifier(joinTable),
            quoteIdentifier(leftColumn), mapJavaTypeToSql(leftIdType, null),
            quoteIdentifier(rightColumn), mapJavaTypeToSql(rightIdType, null),
            quoteIdentifier(leftColumn), quoteIdentifier(rightColumn)
        );
    }

    /**
     * Generates ADD FOREIGN KEY CONSTRAINT DDL.
     * Returns null if the dialect does not support declarative FK via ALTER TABLE (e.g. SQLite).
     */
    default String generateAddForeignKeyConstraint(String tableName, String fkColumn,
                                                   String referencedTable, String referencedColumn) {
        return String.format(
            "ALTER TABLE %s ADD CONSTRAINT %s FOREIGN KEY (%s) REFERENCES %s (%s)",
            quoteIdentifier(tableName),
            quoteIdentifier("fk_" + tableName + "_" + fkColumn),
            quoteIdentifier(fkColumn),
            quoteIdentifier(referencedTable),
            quoteIdentifier(referencedColumn)
        );
    }

    /**
     * Whether the dialect supports partial indexes (WHERE clause on CREATE INDEX).
     */
    default boolean supportsPartialIndexes() {
        return false;
    }
}