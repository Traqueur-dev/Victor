package fr.traqueur.victor.database;

import fr.traqueur.victor.entities.dialect.Dialect;
import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.entities.metadata.FieldMetadata;
import fr.traqueur.victor.exceptions.VictorException;

import java.sql.*;
import java.util.*;

public final class SqlExecutor {

    private final ConnectionManager connectionManager;
    private final Dialect dialect;

    public SqlExecutor(ConnectionManager connectionManager, Dialect dialect) {
        this.connectionManager = connectionManager;
        this.dialect = dialect;
    }

    // ========== Méthodes originales préservées ==========

    /**
     * Execute DDL statement (CREATE, ALTER, DROP)
     */
    public void executeDDL(String sql) {
        if (connectionManager.getConfiguration().showSql()) {
            System.out.println("SQL: " + sql);
        }

        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sql);

        } catch (SQLException e) {
            throw new VictorException("Failed to execute DDL: " + sql, e);
        }
    }

    /**
     * Execute COUNT using dialect
     */
    public long executeCount(EntityMetadata metadata) {
        String sql = dialect.generateCount(metadata);

        if (connectionManager.getConfiguration().showSql()) {
            System.out.println("SQL: " + sql);
        }

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;

        } catch (SQLException e) {
            throw new VictorException("Failed to execute count query: " + sql, e);
        }
    }

    public int executeUpsert(String sql, Object[] params) {
        if (connectionManager.getConfiguration().showSql()) {
            System.out.println("SQL (UPSERT): " + sql);
        }

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (params != null) {
                setParameters(stmt, params);
            }

            return stmt.executeUpdate();

        } catch (SQLException e) {
            throw new VictorException("Failed to execute upsert: " + sql, e);
        }
    }

    public Set<String> executeQueryForStringSet(String sql) {
        if (connectionManager.getConfiguration().showSql()) {
            System.out.println("SQL: " + sql);
        }

        Set<String> results = new HashSet<>();

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String value = rs.getString(1);
                if (value != null) {
                    results.add(value.toLowerCase());
                }
            }

            return results;

        } catch (SQLException e) {
            throw new VictorException("Failed to execute query for string set: " + sql, e);
        }
    }

    /**
     * Execute EXISTS check using dialect
     */
    public boolean executeExists(EntityMetadata metadata, Object id) {
        String sql = dialect.generateExists(metadata);

        if (connectionManager.getConfiguration().showSql()) {
            System.out.println("SQL: " + sql);
        }

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            throw new VictorException("Failed to execute exists query: " + sql, e);
        }
    }

    /**
     * Execute DELETE using dialect
     */
    public void executeDelete(EntityMetadata metadata, Object id) {
        String sql = dialect.generateDelete(metadata);

        if (connectionManager.getConfiguration().showSql()) {
            System.out.println("SQL: " + sql);
        }

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new VictorException("Delete failed, no rows affected for ID: " + id);
            }

        } catch (SQLException e) {
            throw new VictorException("Failed to execute delete: " + sql, e);
        }
    }

    // ========== Nouvelles méthodes pour compatibilité RepositoryProxyHandler ==========

    /**
     * Execute INSERT with generated key - Version RepositoryProxyHandler
     */
    public <T> T executeInsertWithGeneratedKey(String sql, Object[] params, Class<T> idType) {
        if (connectionManager.getConfiguration().showSql()) {
            System.out.println("SQL: " + sql);
        }

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Set parameters
            setParameters(stmt, params);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new VictorException("Insert failed, no rows affected");
            }

            // Get generated key
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getObject(1, idType);
                }
                throw new VictorException("Insert failed, no generated key returned");
            }

        } catch (SQLException e) {
            throw new VictorException("Failed to execute insert: " + sql, e);
        }
    }

    /**
     * Execute UPDATE/DELETE with parameters - Version RepositoryProxyHandler
     */
    public int executeUpdate(String sql, Object[] params) {
        if (connectionManager.getConfiguration().showSql()) {
            System.out.println("SQL: " + sql);
        }

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (params != null) {
                setParameters(stmt, params);
            }

            return stmt.executeUpdate();

        } catch (SQLException e) {
            throw new VictorException("Failed to execute update: " + sql, e);
        }
    }

    /**
     * Execute SELECT query with RowMapper - Version RepositoryProxyHandler
     */
    public <T> List<T> executeQuery(String sql, Object[] params, RowMapper<T> mapper) {
        if (connectionManager.getConfiguration().showSql()) {
            System.out.println("SQL: " + sql);
        }

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (params != null) {
                setParameters(stmt, params);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
                return results;
            }

        } catch (SQLException e) {
            throw new VictorException("Failed to execute query: " + sql, e);
        }
    }

    /**
     * Execute SELECT query returning single result - Version RepositoryProxyHandler
     */
    public <T> T executeQuerySingle(String sql, Object[] params, RowMapper<T> mapper) {
        if (connectionManager.getConfiguration().showSql()) {
            System.out.println("SQL: " + sql);
        }

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (params != null) {
                setParameters(stmt, params);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapper.map(rs);
                }
                return null;
            }

        } catch (SQLException e) {
            throw new VictorException("Failed to execute query single: " + sql, e);
        }
    }

    /**
     * Execute COUNT query with parameters - Version RepositoryProxyHandler
     */
    public long executeCount(String sql, Object[] params) {
        if (connectionManager.getConfiguration().showSql()) {
            System.out.println("SQL: " + sql);
        }

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (params != null) {
                setParameters(stmt, params);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0;
            }

        } catch (SQLException e) {
            throw new VictorException("Failed to execute count: " + sql, e);
        }
    }

    // ========== Méthodes originales préservées ==========

    /**
     * Execute INSERT with generated key using dialect - Version originale
     */
    public <T> T executeInsertWithGeneratedKey(EntityMetadata metadata, Object dto, Class<T> idType) {
        String sql = dialect.generateInsert(metadata);

        if (connectionManager.getConfiguration().showSql()) {
            System.out.println("SQL: " + sql);
        }

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Set parameters from DTO (exclude ID field)
            setInsertParameters(stmt, metadata, dto);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new VictorException("Insert failed, no rows affected");
            }

            // Get generated key
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getObject(1, idType);
                }
                throw new VictorException("Insert failed, no generated key returned");
            }

        } catch (SQLException e) {
            throw new VictorException("Failed to execute insert: " + sql, e);
        }
    }

    /**
     * Execute SELECT by ID using dialect - Version originale
     */
    public <T> Optional<T> executeSelectById(EntityMetadata metadata, Object id, Class<T> resultType) {
        String sql = dialect.generateSelectById(metadata);

        if (connectionManager.getConfiguration().showSql()) {
            System.out.println("SQL: " + sql);
        }

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultToDto(rs, metadata, resultType));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new VictorException("Failed to execute select by ID: " + sql, e);
        }
    }

    /**
     * Execute SELECT ALL using dialect - Version originale
     */
    public <T> List<T> executeSelectAll(EntityMetadata metadata, Class<T> resultType) {
        String sql = dialect.generateSelectAll(metadata);

        if (connectionManager.getConfiguration().showSql()) {
            System.out.println("SQL: " + sql);
        }

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            List<T> results = new ArrayList<>();
            while (rs.next()) {
                results.add(mapResultToDto(rs, metadata, resultType));
            }
            return results;

        } catch (SQLException e) {
            throw new VictorException("Failed to execute select all: " + sql, e);
        }
    }

    // ========== Helper Methods ==========

    private void setParameters(PreparedStatement stmt, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }

    private void setInsertParameters(PreparedStatement stmt, EntityMetadata metadata, Object dto) throws SQLException {
        var nonIdFields = metadata.getNonIdFields();
        int paramIndex = 1;

        for (FieldMetadata field : nonIdFields) {
            Object value = field.getValue(dto);
            stmt.setObject(paramIndex++, value);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T mapResultToDto(ResultSet rs, EntityMetadata metadata, Class<T> resultType) throws SQLException {
        try {
            if (resultType.isRecord()) {
                return mapResultToRecord(rs, metadata, resultType);
            } else {
                return mapResultToClass(rs, metadata, resultType);
            }
        } catch (Exception e) {
            throw new VictorException("Failed to map result to " + resultType.getSimpleName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T mapResultToRecord(ResultSet rs, EntityMetadata metadata, Class<T> recordType) throws Exception {
        var constructor = recordType.getDeclaredConstructors()[0];
        var parameters = constructor.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            FieldMetadata field = metadata.getFields().stream()
                    .filter(f -> f.getField().getName().equals(paramName))
                    .findFirst()
                    .orElse(null);

            if (field != null) {
                args[i] = rs.getObject(field.getColumnName());
            }
        }

        return (T) constructor.newInstance(args);
    }

    @SuppressWarnings("unchecked")
    private <T> T mapResultToClass(ResultSet rs, EntityMetadata metadata, Class<T> clazz) throws Exception {
        T instance = clazz.getDeclaredConstructor().newInstance();

        for (FieldMetadata field : metadata.getFields()) {
            Object value = rs.getObject(field.getColumnName());
            field.setValue(instance, value);
        }

        return instance;
    }

    public Dialect getDialect() {
        return dialect;
    }

    // ✅ AJOUT: Méthode getFieldValue manquante
    /**
     * Extract field value from ResultSet using FieldMetadata
     */
    public Object getFieldValue(ResultSet rs, FieldMetadata fieldMetadata) throws SQLException {
        String columnName = fieldMetadata.getColumnName();
        Class<?> javaType = fieldMetadata.getJavaType();

        try {
            // Handle different Java types appropriately
            if (javaType == String.class) {
                return rs.getString(columnName);
            } else if (javaType == Long.class || javaType == long.class) {
                long value = rs.getLong(columnName);
                return rs.wasNull() ? null : value;
            } else if (javaType == Integer.class || javaType == int.class) {
                int value = rs.getInt(columnName);
                return rs.wasNull() ? null : value;
            } else if (javaType == Boolean.class || javaType == boolean.class) {
                boolean value = rs.getBoolean(columnName);
                return rs.wasNull() ? null : value;
            } else if (javaType == Double.class || javaType == double.class) {
                double value = rs.getDouble(columnName);
                return rs.wasNull() ? null : value;
            } else if (javaType == Float.class || javaType == float.class) {
                float value = rs.getFloat(columnName);
                return rs.wasNull() ? null : value;
            } else if (javaType == Short.class || javaType == short.class) {
                short value = rs.getShort(columnName);
                return rs.wasNull() ? null : value;
            } else if (javaType == Byte.class || javaType == byte.class) {
                byte value = rs.getByte(columnName);
                return rs.wasNull() ? null : value;
            } else if (javaType == java.math.BigDecimal.class) {
                return rs.getBigDecimal(columnName);
            } else if (javaType == java.time.LocalDateTime.class) {
                var timestamp = rs.getTimestamp(columnName);
                return timestamp != null ? timestamp.toLocalDateTime() : null;
            } else if (javaType == java.time.LocalDate.class) {
                var date = rs.getDate(columnName);
                return date != null ? date.toLocalDate() : null;
            } else if (javaType == java.time.LocalTime.class) {
                var time = rs.getTime(columnName);
                return time != null ? time.toLocalTime() : null;
            } else if (javaType == java.sql.Timestamp.class) {
                return rs.getTimestamp(columnName);
            } else if (javaType == java.sql.Date.class) {
                return rs.getDate(columnName);
            } else if (javaType == java.sql.Time.class) {
                return rs.getTime(columnName);
            } else if (javaType == byte[].class) {
                return rs.getBytes(columnName);
            } else {
                // Fallback pour types non supportés
                return rs.getObject(columnName);
            }
        } catch (SQLException e) {
            throw new VictorException("Failed to extract field value for column " + columnName +
                    " of type " + javaType.getSimpleName(), e);
        }
    }

    // ========== Interface RowMapper ==========

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}