package fr.traqueur.victor.database.query;

import fr.traqueur.victor.entity.dialect.Dialect;
import fr.traqueur.victor.entity.metadata.EntityMetadata;
import fr.traqueur.victor.entity.metadata.FieldMetadata;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.utils.StringUtils;
import fr.traqueur.victor.utils.VictorLogger;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
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
     * Génère le SQL d'une query parsée sans connaître les arguments.
     * Les clauses {@code IN}/{@code NotIn} utilisent un seul placeholder
     * (template). Préférer {@link #generateSql(MethodNameParser.ParsedQuery, Object[])}
     * au runtime pour l'expansion réelle des placeholders.
     */
    public String generateSql(MethodNameParser.ParsedQuery parsedQuery) {
        return generateSql(parsedQuery, null);
    }

    /**
     * Génère le SQL complet pour une query parsée, en expansant les placeholders
     * {@code IN}/{@code NotIn} selon la taille réelle des arguments collection.
     *
     * @param args les arguments effectifs de la méthode (peut être {@code null}
     *             pour le mode template).
     */
    public String generateSql(MethodNameParser.ParsedQuery parsedQuery, Object[] args) {
        StringBuilder sql = new StringBuilder();

        sql.append(switch (parsedQuery.kind()) {
            case FIND -> "SELECT * FROM ";
            case COUNT -> "SELECT COUNT(*) FROM ";
            case EXISTS -> "SELECT 1 FROM ";
            case DELETE -> "DELETE FROM ";
        });
        sql.append(getFullTableName());

        if (!parsedQuery.conditions().isEmpty()) {
            sql.append(" WHERE ");
            sql.append(buildWhereClause(parsedQuery.conditions(), args));
        }

        if (parsedQuery.kind() == MethodNameParser.QueryKind.FIND && !parsedQuery.orderBy().isEmpty()) {
            sql.append(" ORDER BY ");
            sql.append(buildOrderByClause(parsedQuery.orderBy()));
        }

        if (parsedQuery.kind() == MethodNameParser.QueryKind.EXISTS) {
            sql.append(" LIMIT 1");
        }

        String generatedSql = sql.toString();

        if (showSql) {
            VictorLogger.debug("Generated sql: " + generatedSql);
        }

        return generatedSql;
    }

    /**
     * Construit la clause WHERE complète.
     * <p>Le parcours des conditions suit le même ordre d'index d'argument que
     * {@code RepositoryProxyHandler.prepareArguments} : seules les conditions
     * qui requièrent un paramètre consomment un argument.</p>
     */
    private String buildWhereClause(List<MethodNameParser.WhereCondition> conditions, Object[] args) {
        List<String> parts = new ArrayList<>();

        int argIndex = 0;
        for (MethodNameParser.WhereCondition condition : conditions) {
            String fieldName = StringUtils.camelToSnakeCase(condition.fieldName());
            String columnName = findColumnName(fieldName);

            Object arg = null;
            boolean hasArg = false;
            if (condition.requiresParameter()) {
                if (args != null && argIndex < args.length) {
                    arg = args[argIndex];
                    hasArg = true;
                }
                argIndex++;
            }

            parts.add(buildCondition(columnName, condition.operator(), arg, hasArg));
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

    private String buildCondition(String columnName, String operator, Object arg, boolean hasArg) {
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
            case "In" -> buildInCondition(quotedColumn, arg, hasArg, false);
            case "NotIn" -> buildInCondition(quotedColumn, arg, hasArg, true);
            default -> throw new VictorException("Unsupported operator: " + operator);
        };
    }

    /**
     * Construit une clause {@code IN}/{@code NOT IN} avec autant de placeholders
     * que d'éléments dans la collection. En mode template (args inconnus) un seul
     * placeholder est émis. Une collection vide produit une condition toujours
     * fausse ({@code 1=0}) pour {@code IN}, toujours vraie ({@code 1=1}) pour
     * {@code NOT IN}.
     */
    private String buildInCondition(String quotedColumn, Object arg, boolean hasArg, boolean negate) {
        String operator = negate ? "NOT IN" : "IN";

        if (!hasArg) {
            // Template mode (no runtime args): single placeholder.
            return quotedColumn + " " + operator + " (?)";
        }

        int size = collectionSize(arg);
        if (size == 0) {
            return negate ? "1=1" : "1=0";
        }

        String placeholders = "?" + ", ?".repeat(size - 1);
        return quotedColumn + " " + operator + " (" + placeholders + ")";
    }

    /** Number of elements an IN/NotIn argument expands to (1 for a scalar argument). */
    private int collectionSize(Object arg) {
        if (arg == null) {
            return 0;
        }
        if (arg instanceof Collection<?> collection) {
            return collection.size();
        }
        if (arg.getClass().isArray()) {
            return Array.getLength(arg);
        }
        return 1;
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
                String fieldName = StringUtils.camelToSnakeCase(clause.fieldName());
                String columnName = findColumnName(fieldName);
                return dialect.quoteIdentifier(columnName) + " " + clause.direction();
            })
            .collect(Collectors.joining(", "));
    }

    private String findColumnName(String fieldName) {
        for (FieldMetadata field : entityMetadata.getScalarFields()) {
            if (field.getFieldName().equalsIgnoreCase(fieldName)) {
                return field.getColumnName();
            }
        }
        return fieldName;
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
