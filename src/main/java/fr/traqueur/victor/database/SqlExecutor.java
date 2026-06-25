package fr.traqueur.victor.database;

import fr.traqueur.victor.entity.dialect.Dialect;
import fr.traqueur.victor.entity.metadata.FieldMetadata;
import fr.traqueur.victor.entity.transaction.TransactionContext;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.managers.ConnectionManager;
import fr.traqueur.victor.utils.VictorLogger;

import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.UUID;

/**
 * Executes SQL statements with proper resource management and logging.
 */
public record SqlExecutor(ConnectionManager connectionManager, Dialect dialect) {

    public boolean isShowSql() {
        return connectionManager.getConfiguration().showSql();
    }

    /**
     * Execute DDL statement (CREATE, ALTER, DROP)
     */
    public void executeDDL(String sql) {
        if (connectionManager.getConfiguration().showSql()) {
            VictorLogger.debug("SQL: {}", sql);
        }

        Connection conn = connectionManager.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new VictorException("Failed to execute DDL: " + sql, e);
        } finally {
            closeConnectionIfNotTransactional(conn);
        }
    }

    public int executeUpsert(String sql, Object[] params) {
        if (connectionManager.getConfiguration().showSql()) {
            VictorLogger.debug("SQL (UPSERT): {}", sql);
        }

        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (params != null) {
                setParameters(stmt, params);
            }
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new VictorException("Failed to execute upsert: " + sql, e);
        } finally {
            closeConnectionIfNotTransactional(conn);
        }
    }

    public Set<String> executeQueryForStringSet(String sql) {
        if (connectionManager.getConfiguration().showSql()) {
            VictorLogger.debug("SQL: {}", sql);
        }

        Set<String> results = new HashSet<>();
        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
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
        } finally {
            closeConnectionIfNotTransactional(conn);
        }
    }

    public <T> T executeInsertWithGeneratedKey(String sql, Object[] params, Class<T> idType) {
        if (connectionManager.getConfiguration().showSql()) {
            VictorLogger.debug("SQL: {}", sql);
        }

        Connection conn = connectionManager.getConnection();

        // Check if dialect supports standard generated keys
        if (!dialect.supportsStandardGeneratedKeys()) {
            // Use dialect-specific method (e.g., SQLite's last_insert_rowid())
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                setParameters(stmt, params);

                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new VictorException("Insert failed, no rows affected");
                }

                // Execute dialect-specific query to get last inserted ID
                String lastIdSql = dialect.getLastInsertIdSql();
                try (PreparedStatement idStmt = conn.prepareStatement(lastIdSql);
                     ResultSet rs = idStmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getObject(1, idType);
                    }
                    throw new VictorException("Insert failed, no generated key returned");
                }
            } catch (SQLException e) {
                throw new VictorException("Failed to execute insert: " + sql, e);
            } finally {
                closeConnectionIfNotTransactional(conn);
            }
        }

        // Standard JDBC approach for most databases
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParameters(stmt, params);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new VictorException("Insert failed, no rows affected");
            }

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getObject(1, idType);
                }
                throw new VictorException("Insert failed, no generated key returned");
            }
        } catch (SQLException e) {
            throw new VictorException("Failed to execute insert: " + sql, e);
        } finally {
            closeConnectionIfNotTransactional(conn);
        }
    }

    /**
     * Executes a batch of statements (same SQL, many parameter sets) using
     * JDBC addBatch/executeBatch on a single connection. When not already in a
     * transaction, the whole batch is committed atomically.
     */
    public int[] executeBatch(String sql, List<Object[]> batchParams) {
        if (batchParams == null || batchParams.isEmpty()) {
            return new int[0];
        }
        if (connectionManager.getConfiguration().showSql()) {
            VictorLogger.debug("SQL (BATCH x{}): {}", batchParams.size(), sql);
        }

        Connection conn = connectionManager.getConnection();
        boolean inTransaction = TransactionContext.getCurrentConnection() == conn;
        boolean previousAutoCommit = true;
        try {
            if (!inTransaction) {
                previousAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Object[] params : batchParams) {
                    setParameters(stmt, params);
                    stmt.addBatch();
                }
                int[] result = stmt.executeBatch();
                if (!inTransaction) {
                    conn.commit();
                }
                return result;
            }
        } catch (SQLException e) {
            if (!inTransaction) {
                rollbackQuietly(conn);
            }
            throw new VictorException("Failed to execute batch: " + sql, e);
        } finally {
            if (!inTransaction) {
                restoreAutoCommitQuietly(conn, previousAutoCommit);
            }
            closeConnectionIfNotTransactional(conn);
        }
    }

    /**
     * Batch insert returning the generated keys in insertion order. Falls back
     * to per-row inserts for dialects without standard generated-key support
     * (e.g. SQLite, using last_insert_rowid()). Atomic when not already in a
     * transaction.
     */
    public <T> List<T> executeBatchInsertWithGeneratedKeys(String sql, List<Object[]> batchParams, Class<T> idType) {
        if (batchParams == null || batchParams.isEmpty()) {
            return List.of();
        }
        if (connectionManager.getConfiguration().showSql()) {
            VictorLogger.debug("SQL (BATCH INSERT x{}): {}", batchParams.size(), sql);
        }

        Connection conn = connectionManager.getConnection();
        boolean inTransaction = TransactionContext.getCurrentConnection() == conn;
        boolean previousAutoCommit = true;
        try {
            if (!inTransaction) {
                previousAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
            }

            List<T> ids = dialect.supportsStandardGeneratedKeys()
                    ? batchInsertStandard(conn, sql, batchParams, idType)
                    : batchInsertWithLastId(conn, sql, batchParams, idType);

            if (!inTransaction) {
                conn.commit();
            }
            return ids;
        } catch (SQLException e) {
            if (!inTransaction) {
                rollbackQuietly(conn);
            }
            throw new VictorException("Failed to execute batch insert: " + sql, e);
        } finally {
            if (!inTransaction) {
                restoreAutoCommitQuietly(conn, previousAutoCommit);
            }
            closeConnectionIfNotTransactional(conn);
        }
    }

    private <T> List<T> batchInsertStandard(Connection conn, String sql, List<Object[]> batchParams, Class<T> idType)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (Object[] params : batchParams) {
                setParameters(stmt, params);
                stmt.addBatch();
            }
            stmt.executeBatch();
            List<T> ids = new ArrayList<>(batchParams.size());
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                while (rs.next()) {
                    ids.add(rs.getObject(1, idType));
                }
            }
            return ids;
        }
    }

    private <T> List<T> batchInsertWithLastId(Connection conn, String sql, List<Object[]> batchParams, Class<T> idType)
            throws SQLException {
        String lastIdSql = dialect.getLastInsertIdSql();
        List<T> ids = new ArrayList<>(batchParams.size());
        for (Object[] params : batchParams) {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                setParameters(stmt, params);
                if (stmt.executeUpdate() == 0) {
                    throw new VictorException("Batch insert failed, no rows affected");
                }
            }
            try (PreparedStatement idStmt = conn.prepareStatement(lastIdSql);
                 ResultSet rs = idStmt.executeQuery()) {
                if (rs.next()) {
                    ids.add(rs.getObject(1, idType));
                } else {
                    throw new VictorException("Batch insert failed, no generated key returned");
                }
            }
        }
        return ids;
    }

    private void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException e) {
            VictorLogger.warn("Failed to rollback batch", e);
        }
    }

    private void restoreAutoCommitQuietly(Connection conn, boolean autoCommit) {
        try {
            conn.setAutoCommit(autoCommit);
        } catch (SQLException e) {
            VictorLogger.warn("Failed to restore auto-commit", e);
        }
    }

    /**
     * Execute UPDATE/DELETE with parameters - Version RepositoryProxyHandler
     */
    public int executeUpdate(String sql, Object[] params) {
        if (connectionManager.getConfiguration().showSql()) {
            VictorLogger.debug("SQL: {}", sql);
        }

        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (params != null) {
                setParameters(stmt, params);
            }
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new VictorException("Failed to execute update: " + sql, e);
        } finally {
            closeConnectionIfNotTransactional(conn);
        }
    }

    /**
     * Execute SELECT query with RowMapper - Version RepositoryProxyHandler
     */
    public <T> List<T> executeQuery(String sql, Object[] params, RowMapper<T> mapper) {
        if (connectionManager.getConfiguration().showSql()) {
            VictorLogger.debug("SQL: {}", sql);
        }

        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (params != null) {
                setParameters(stmt, params);
            }

            List<T> results = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
            }
            return results;

        } catch (SQLException e) {
            throw new VictorException("Failed to execute query: " + sql, e);
        } finally {
            closeConnectionIfNotTransactional(conn);
        }
    }

    public <T> T executeQuerySingle(String sql, Object[] params, RowMapper<T> mapper) {
        if (connectionManager.getConfiguration().showSql()) {
            VictorLogger.debug("SQL: {}", sql);
        }

        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        } finally {
            closeConnectionIfNotTransactional(conn);
        }
    }

    public long executeCount(String sql, Object[] params) {
        if (connectionManager.getConfiguration().showSql()) {
            VictorLogger.debug("SQL: {}", sql);
        }

        Connection conn = connectionManager.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        } finally {
            closeConnectionIfNotTransactional(conn);
        }
    }

    private void setParameters(PreparedStatement stmt, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object param = convertForJdbc(params[i]);
            stmt.setObject(i + 1, param);
        }
    }

    /**
     * Converts Java objects to JDBC-compatible types.
     * Some types like UUID need to be converted to String to avoid
     * binary serialization issues with certain JDBC drivers.
     */
    private Object convertForJdbc(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        return value;
    }

    public Object getFieldValue(ResultSet rs, FieldMetadata fieldMetadata) {
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
            } else if (javaType == BigDecimal.class) {
                return rs.getBigDecimal(columnName);
            } else if (javaType == LocalDateTime.class) {
                var timestamp = rs.getTimestamp(columnName);
                return timestamp != null ? timestamp.toLocalDateTime() : null;
            } else if (javaType == LocalDate.class) {
                var date = rs.getDate(columnName);
                return date != null ? date.toLocalDate() : null;
            } else if (javaType == LocalTime.class) {
                var time = rs.getTime(columnName);
                return time != null ? time.toLocalTime() : null;
            } else if (javaType == Timestamp.class) {
                return rs.getTimestamp(columnName);
            } else if (javaType == Date.class) {
                return rs.getDate(columnName);
            } else if (javaType == Time.class) {
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

    private void closeConnectionIfNotTransactional(Connection connection) {
        try {
            if (TransactionContext.getCurrentConnection() != connection) {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            }
        } catch (SQLException e) {
            VictorLogger.warn("Failed to close connection", e);
        }
    }

    // ========== Interface RowMapper ==========

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}