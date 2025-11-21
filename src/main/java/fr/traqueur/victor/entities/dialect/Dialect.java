package fr.traqueur.victor.entities.dialect;

import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.entities.metadata.FieldMetadata;

import java.util.Properties;

public interface Dialect {

    String getName();

    String getDriverClassName();

    boolean supportsUrl(String jdbcUrl);

    String buildConnectionUrl(String host, int port, String database, Properties properties);

    Properties getDefaultConnectionProperties();

    String generateCreateTable(EntityMetadata metadata);

    String generateInsert(EntityMetadata metadata);

    String generateUpdate(EntityMetadata metadata);

    String generateUpsert(EntityMetadata metadata);

    String generateDelete(EntityMetadata metadata);

    String generateSelectById(EntityMetadata metadata);

    String generateSelectAll(EntityMetadata metadata);

    String generateCount(EntityMetadata metadata);

    String generateExists(EntityMetadata metadata);

    String mapJavaTypeToSql(Class<?> javaType, FieldMetadata fieldMetadata);

    String quoteIdentifier(String identifier);

    String escapeLikePattern(String pattern);

    boolean supportsSchemas();

    boolean supportsSequences();

    boolean supportsJsonType();

    boolean isEmbedded();

    String getAutoIncrementSyntax(Class<?> idType);

    default String generateCreateSchema(String schemaName) {
        if (supportsSchemas()) {
            return "CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(schemaName);
        }
        return null;
    }

    default String[] getConnectionSetupSql() {
        return new String[0];
    }
}