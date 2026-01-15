package fr.traqueur.victor.entities.dialect;

import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.entities.metadata.FieldMetadata;

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

    boolean supportsSequences();

    boolean supportsJsonType();

    boolean isEmbedded();

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
}