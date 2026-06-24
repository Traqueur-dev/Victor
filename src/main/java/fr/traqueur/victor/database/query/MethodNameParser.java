package fr.traqueur.victor.database.query;

import fr.traqueur.victor.entity.Query;
import fr.traqueur.victor.exceptions.VictorException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MethodNameParser {

    private static final Pattern FIND_BY_PATTERN = Pattern.compile("^findBy(.+)$");
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("(.*)OrderBy(.+)$");

    private static final String[] OPERATORS = {
            "GreaterThanEqual",
            "LessThanEqual",
            "NotEqual",
            "GreaterThan",
            "LessThan",
            "NotLike",
            "Like",
            "IsNull",
            "IsNotNull",
            "NotIn",
            "In"
    };

    private MethodNameParser() {}

    public static ParsedQuery parse(Method method) {
        String methodName = method.getName();

        Matcher findByMatcher = FIND_BY_PATTERN.matcher(methodName);
        if (!findByMatcher.matches()) {
            throw new VictorException(
                    "Dynamic query method must start with 'findBy': " + methodName
            );
        }

        String afterFindBy = findByMatcher.group(1);

        String whereClause = afterFindBy;
        List<OrderByClause> orderByClauses = new ArrayList<>();

        Matcher orderByMatcher = ORDER_BY_PATTERN.matcher(afterFindBy);
        if (orderByMatcher.matches()) {
            whereClause = orderByMatcher.group(1);
            String orderByPart = orderByMatcher.group(2);
            orderByClauses = parseOrderBy(orderByPart);
        }

        List<WhereCondition> conditions = parseWhereConditions(whereClause);

        validateParameterCount(method, conditions);

        return new ParsedQuery(conditions, orderByClauses);
    }

    private static List<WhereCondition> parseWhereConditions(String whereClause) {
        List<WhereCondition> conditions = new ArrayList<>();

        String[] parts = whereClause.split("(And|Or)");

        List<String> connectors = extractConnectors(whereClause);

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();

            FieldOperator fieldOp = extractFieldAndOperator(part);

            String connector = i < connectors.size() ? connectors.get(i) : null;

            conditions.add(new WhereCondition(
                    fieldOp.field(),
                    fieldOp.operator(),
                    connector
            ));
        }

        return conditions;
    }

    private static List<String> extractConnectors(String whereClause) {
        List<String> connectors = new ArrayList<>();
        Pattern pattern = Pattern.compile("(And|Or)");
        Matcher matcher = pattern.matcher(whereClause);

        while (matcher.find()) {
            connectors.add(matcher.group(1).toUpperCase());
        }

        return connectors;
    }

    private static FieldOperator extractFieldAndOperator(String part) {
        for (String operator : OPERATORS) {
            if (part.endsWith(operator)) {
                String field = part.substring(0, part.length() - operator.length());
                return new FieldOperator(field, operator);
            }
        }

        return new FieldOperator(part, "Equal");
    }

    private static List<OrderByClause> parseOrderBy(String orderByPart) {
        List<OrderByClause> clauses = new ArrayList<>();

        String[] parts = orderByPart.split("And");

        for (String part : parts) {
            part = part.trim();

            Query.Order direction = Query.Order.ASC;
            String field = part;

            if (part.endsWith("Desc")) {
                direction = Query.Order.DESC;
                field = part.substring(0, part.length() - 4);
            } else if (part.endsWith("Asc")) {
                field = part.substring(0, part.length() - 3);
            }

            clauses.add(new OrderByClause(field, direction));
        }

        return clauses;
    }

    private static void validateParameterCount(Method method, List<WhereCondition> conditions) {
        int expectedParams = (int) conditions.stream()
                .filter(WhereCondition::requiresParameter)
                .count();

        int actualParams = method.getParameterCount();

        if (expectedParams != actualParams) {
            throw new VictorException(
                    String.format(
                            "Method %s expects %d parameter(s) but has %d",
                            method.getName(),
                            expectedParams,
                            actualParams
                    )
            );
        }
    }

    private record FieldOperator(String field, String operator) {}

    public record ParsedQuery(
            List<WhereCondition> conditions,
            List<OrderByClause> orderBy
    ) {}

    /**
     * Une condition WHERE.
     */
    public record WhereCondition(
            String fieldName,
            String operator,
            String connector  // AND, OR, ou null pour la dernière condition
    ) {
        public boolean requiresParameter() {
            return !operator.equals("IsNull") && !operator.equals("IsNotNull");
        }
    }

    /**
     * Une clause ORDER BY.
     */
    public record OrderByClause(
            String fieldName,
            Query.Order direction  // ASC ou DESC
    ) {}

}
