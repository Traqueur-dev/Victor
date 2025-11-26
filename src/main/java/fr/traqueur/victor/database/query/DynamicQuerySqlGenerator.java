package fr.traqueur.victor.database.query;

import fr.traqueur.victor.entities.dialect.Dialect;
import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.entities.metadata.FieldMetadata;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.utils.VictorLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Génère le SQL pour les queries dynamiques basées sur les noms de méthodes.
 */
public final class DynamicQuerySqlGenerator {
    
    private final EntityMetadata entityMetadata;
    private final Dialect dialect;
    private final boolean showSql;
    
    public DynamicQuerySqlGenerator(EntityMetadata entityMetadata, Dialect dialect, boolean showSql) {
        this.entityMetadata = entityMetadata;
        this.dialect = dialect;
        this.showSql = showSql;
    }
    
    /**
     * Génère le SQL complet pour une query parsée.
     */
    public String generateSql(MethodNameParser.ParsedQuery parsedQuery) {
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT * FROM ");
        sql.append(getFullTableName());

        if (!parsedQuery.conditions().isEmpty()) {
            sql.append(" WHERE ");
            sql.append(buildWhereClause(parsedQuery.conditions()));
        }

        if (!parsedQuery.orderBy().isEmpty()) {
            sql.append(" ORDER BY ");
            sql.append(buildOrderByClause(parsedQuery.orderBy()));
        }
        
        String generatedSql = sql.toString();

        if (showSql) {
            VictorLogger.debug("Generated sql: " + generatedSql);
        }
        
        return generatedSql;
    }
    
    /**
     * Construit la clause WHERE complète.
     */
    private String buildWhereClause(List<MethodNameParser.WhereCondition> conditions) {
        List<String> parts = new ArrayList<>();
        
        for (MethodNameParser.WhereCondition condition : conditions) {
            String fieldName = toSnakeCase(condition.fieldName());
            String columnName = findColumnName(fieldName);
            String sqlCondition = buildCondition(columnName, condition.operator());
            
            parts.add(sqlCondition);
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            result.append(parts.get(i));
            
            if (i < parts.size() - 1) {
                String connector = conditions.get(i).connector();
                result.append(" ").append(connector).append(" ");
            }
        }
        
        return result.toString();
    }

    private String buildCondition(String columnName, String operator) {
        String quotedColumn = dialect.quoteIdentifier(columnName);
        
        return switch (operator) {
            case "Equal" -> quotedColumn + " = ?";
            case "NotEqual" -> quotedColumn + " != ?";
            case "GreaterThan" -> quotedColumn + " > ?";
            case "GreaterThanEqual" -> quotedColumn + " >= ?";
            case "LessThan" -> quotedColumn + " < ?";
            case "LessThanEqual" -> quotedColumn + " <= ?";

            case "Like" -> buildLikeCondition(quotedColumn, false);
            case "NotLike" -> buildLikeCondition(quotedColumn, true);
            
            case "IsNull" -> quotedColumn + " IS NULL";
            case "IsNotNull" -> quotedColumn + " IS NOT NULL";
            case "In" -> quotedColumn + " IN (?)";
            case "NotIn" -> quotedColumn + " NOT IN (?)";
            default -> throw new VictorException("Unsupported operator: " + operator);
        };
    }

    private String buildLikeCondition(String quotedColumn, boolean negate) {
        String operator = negate ? "NOT LIKE" : "LIKE";

        String dialectName = dialect.getName().toLowerCase();
        if (dialectName.equals("postgresql") || dialectName.equals("sqlite")) {
            return quotedColumn + " " + operator + " ? ESCAPE '\\'";
        }

        return quotedColumn + " " + operator + " ?";
    }

    private String buildOrderByClause(List<MethodNameParser.OrderByClause> orderByClauses) {
        return orderByClauses.stream()
            .map(clause -> {
                String fieldName = toSnakeCase(clause.fieldName());
                String columnName = findColumnName(fieldName);
                return dialect.quoteIdentifier(columnName) + " " + clause.direction();
            })
            .collect(Collectors.joining(", "));
    }

    private String findColumnName(String fieldName) {
        for (FieldMetadata field : entityMetadata.getFields()) {
            if (field.getField().getName().equalsIgnoreCase(fieldName)) {
                return field.getColumnName();
            }
        }

        return fieldName;
    }

    private String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }


    private String getFullTableName() {
        if (entityMetadata.getSchema() != null) {
            return dialect.quoteIdentifier(entityMetadata.getSchema()) + "." + 
                   dialect.quoteIdentifier(entityMetadata.getTableName());
        } else {
            return dialect.quoteIdentifier(entityMetadata.getTableName());
        }
    }
}