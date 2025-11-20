package fr.traqueur.victor.database;

import fr.traqueur.victor.VictorConfiguration;
import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.entities.metadata.FieldMetadata;
import fr.traqueur.victor.exceptions.VictorException;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class SqlExecutor {
    
    private final ConnectionManager connectionManager;
    private final VictorConfiguration configuration;
    
    public SqlExecutor(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.configuration = connectionManager.getConfiguration();
    }
    
    // ========== SELECT Operations ==========
    
    public <T> List<T> executeQuery(String sql, Object[] params, RowMapper<T> mapper) {
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            setParameters(stmt, params);
            logSql(sql, params);
            
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
    
    public <T> T executeQuerySingle(String sql, Object[] params, RowMapper<T> mapper) {
        List<T> results = executeQuery(sql, params, mapper);
        return results.isEmpty() ? null : results.getFirst();
    }
    
    public long executeCount(String sql, Object[] params) {
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            setParameters(stmt, params);
            logSql(sql, params);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
            
        } catch (SQLException e) {
            throw new VictorException("Failed to execute count query: " + sql, e);
        }
    }
    
    // ========== INSERT/UPDATE/DELETE Operations ==========
    
    public int executeUpdate(String sql, Object[] params) {
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            setParameters(stmt, params);
            logSql(sql, params);
            
            return stmt.executeUpdate();
            
        } catch (SQLException e) {
            throw new VictorException("Failed to execute update: " + sql, e);
        }
    }
    
    public <T> T executeInsertWithGeneratedKey(String sql, Object[] params, Class<T> keyType) {
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            setParameters(stmt, params);
            logSql(sql, params);
            
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new VictorException("Insert failed, no rows affected");
            }
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return convertValue(rs.getObject(1), keyType);
                } else {
                    throw new VictorException("Insert failed, no generated key obtained");
                }
            }
            
        } catch (SQLException e) {
            throw new VictorException("Failed to execute insert: " + sql, e);
        }
    }
    
    // ========== DDL Operations ==========
    
    public void executeDDL(String sql) {
        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement()) {
            
            logSql(sql, null);
            stmt.execute(sql);
            
        } catch (SQLException e) {
            throw new VictorException("Failed to execute DDL: " + sql, e);
        }
    }
    
    // ========== Utility Methods ==========
    
    private void setParameters(PreparedStatement stmt, Object[] params) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                setParameter(stmt, i + 1, params[i]);
            }
        }
    }
    
    private void setParameter(PreparedStatement stmt, int index, Object value) throws SQLException {
        switch (value) {
            case null -> stmt.setNull(index, Types.NULL);
            case String s -> stmt.setString(index, s);
            case Integer i -> stmt.setInt(index, i);
            case Long l -> stmt.setLong(index, l);
            case Boolean b -> stmt.setBoolean(index, b);
            case Double v -> stmt.setDouble(index, v);
            case Float v -> stmt.setFloat(index, v);
            case LocalDateTime localDateTime -> stmt.setTimestamp(index, Timestamp.valueOf(localDateTime));
            default -> stmt.setObject(index, value);
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T> T convertValue(Object value, Class<T> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return (T) value;
        
        if (targetType == Long.class && value instanceof Number) {
            return (T) Long.valueOf(((Number) value).longValue());
        }
        if (targetType == Integer.class && value instanceof Number) {
            return (T) Integer.valueOf(((Number) value).intValue());
        }
        if (targetType == String.class) {
            return (T) value.toString();
        }
        
        return (T) value;
    }
    
    private void logSql(String sql, Object[] params) {
        if (configuration.showSql()) {
            System.out.println("SQL: " + sql);
            if (params != null && params.length > 0) {
                System.out.println("Parameters: " + java.util.Arrays.toString(params));
            }
        }
    }
    
    // ========== Row Mapper Interface ==========
    
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
    
    // ========== Entity-specific Methods ==========
    
    public Object getFieldValue(ResultSet rs, FieldMetadata field) throws SQLException {
        String columnName = field.getColumnName();
        Class<?> javaType = field.getJavaType();
        
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
        } else if (javaType == LocalDateTime.class) {
            Timestamp ts = rs.getTimestamp(columnName);
            return ts != null ? ts.toLocalDateTime() : null;
        } else {
            return rs.getObject(columnName);
        }
    }
}