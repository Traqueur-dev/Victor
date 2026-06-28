package fr.traqueur.victor.database.query;

import fr.traqueur.victor.entity.Query;
import fr.traqueur.victor.exceptions.VictorException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MethodNameParser {

    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("(.*)OrderBy(.+)$");

    /** Supported derived-query prefixes, ordered so longer prefixes are tested first. */
    private static final List<PrefixDef> PREFIXES = List.of(
            new PrefixDef("findBy", QueryKind.FIND),
            new PrefixDef("countBy", QueryKind.COUNT),
            new PrefixDef("existsBy", QueryKind.EXISTS),
            new PrefixDef("deleteBy", QueryKind.DELETE)
    );

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

    /** Operators ordered by descending length, for greedy longest-match while tokenizing. */
    private static final List<String> OPERATORS_BY_LENGTH = Arrays.stream(OPERATORS)
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();

    private MethodNameParser() {}

    /**
     * Legacy entry point that parses purely from the method name without knowledge
     * of the entity's fields. Kept for backward compatibility; prefer
     * {@link #parse(Method, Set)} which disambiguates field names containing
     * {@code And}/{@code Or} (e.g. {@code findByIsOrdered}).
     */
    @Deprecated
    public static ParsedQuery parse(Method method) {
        return parse(method, Set.of());
    }

    /**
     * Parses a derived-query method name into a {@link ParsedQuery}.
     *
     * @param method          the repository method
     * @param knownFieldNames the entity's scalar field names (Java names). When
     *                        non-empty, a field-aware tokenizer is used to split
     *                        the predicate, which removes the ambiguity of the
     *                        naive {@code And}/{@code Or} split.
     */
    public static ParsedQuery parse(Method method, Set<String> knownFieldNames) {
        String methodName = method.getName();

        PrefixDef prefix = detectPrefix(methodName);
        if (prefix == null) {
            throw new VictorException(
                    "Dynamic query method must start with findBy/countBy/existsBy/deleteBy: " + methodName
            );
        }

        String afterBy = methodName.substring(prefix.prefix().length());

        String whereClause = afterBy;
        List<OrderByClause> orderByClauses = new ArrayList<>();

        // ORDER BY only makes sense for FIND queries.
        if (prefix.kind() == QueryKind.FIND) {
            Matcher orderByMatcher = ORDER_BY_PATTERN.matcher(afterBy);
            if (orderByMatcher.matches()) {
                whereClause = orderByMatcher.group(1);
                orderByClauses = parseOrderBy(orderByMatcher.group(2));
            }
        }

        List<WhereCondition> conditions = parseConditions(whereClause, knownFieldNames);

        validateParameterCount(method, conditions);

        return new ParsedQuery(prefix.kind(), conditions, orderByClauses);
    }

    private static PrefixDef detectPrefix(String methodName) {
        for (PrefixDef def : PREFIXES) {
            if (methodName.startsWith(def.prefix()) && methodName.length() > def.prefix().length()) {
                return def;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Condition parsing: field-aware tokenizer with legacy fallback
    // -------------------------------------------------------------------------

    private static List<WhereCondition> parseConditions(String whereClause, Set<String> knownFieldNames) {
        if (!knownFieldNames.isEmpty()) {
            List<WhereCondition> tokenized = tokenizeWithFields(whereClause, knownFieldNames);
            if (tokenized != null) {
                return tokenized;
            }
        }
        return parseWhereConditionsLegacy(whereClause);
    }

    /**
     * Field-aware tokenizer. Walks the predicate left to right, greedily matching
     * the longest known field name, then an optional operator suffix, then an
     * optional {@code And}/{@code Or} connector. Returns {@code null} when the
     * predicate cannot be fully tokenized (caller falls back to the legacy split).
     */
    private static List<WhereCondition> tokenizeWithFields(String predicate, Set<String> knownFieldNames) {
        // Capitalized field names sorted longest-first for greedy matching.
        List<String> capitalizedFields = knownFieldNames.stream()
                .filter(f -> f != null && !f.isEmpty())
                .map(MethodNameParser::capitalize)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();

        List<WhereCondition> conditions = new ArrayList<>();
        int i = 0;
        while (i < predicate.length()) {
            ParsedSegment segment = parseSegment(predicate, i, capitalizedFields);
            if (segment == null) {
                return null; // cannot tokenize -> fallback
            }
            conditions.add(new WhereCondition(segment.field(), segment.operator(), segment.connector()));
            i = segment.nextIndex();
            // A connector must be followed by another segment; no connector means we must be at the end.
            if (segment.connector() == null && i != predicate.length()) {
                return null;
            }
        }
        return conditions.isEmpty() ? null : conditions;
    }

    private static ParsedSegment parseSegment(String predicate, int start, List<String> capitalizedFields) {
        for (String field : capitalizedFields) {
            if (!predicate.startsWith(field, start)) {
                continue;
            }
            int afterField = start + field.length();
            String rest = predicate.substring(afterField);

            // Optional operator suffix (longest match).
            String operator = "Equal";
            String afterOperator = rest;
            for (String op : OPERATORS_BY_LENGTH) {
                if (rest.startsWith(op)) {
                    operator = op;
                    afterOperator = rest.substring(op.length());
                    break;
                }
            }

            // What remains must be empty, or a connector introducing the next segment.
            if (afterOperator.isEmpty()) {
                return new ParsedSegment(field, operator, null, predicate.length());
            }
            if (afterOperator.startsWith("And")) {
                int next = afterField + (rest.length() - afterOperator.length()) + "And".length();
                return new ParsedSegment(field, operator, "AND", next);
            }
            if (afterOperator.startsWith("Or")) {
                int next = afterField + (rest.length() - afterOperator.length()) + "Or".length();
                return new ParsedSegment(field, operator, "OR", next);
            }
            // This field candidate doesn't lead to a valid continuation; try a shorter one.
        }
        return null;
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // -------------------------------------------------------------------------
    // Legacy parsing (naive And/Or split) — fallback only
    // -------------------------------------------------------------------------

    private static List<WhereCondition> parseWhereConditionsLegacy(String whereClause) {
        List<WhereCondition> conditions = new ArrayList<>();

        String[] parts = whereClause.split("(And|Or)");
        List<String> connectors = extractConnectors(whereClause);

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            FieldOperator fieldOp = extractFieldAndOperator(part);
            String connector = i < connectors.size() ? connectors.get(i) : null;
            conditions.add(new WhereCondition(fieldOp.field(), fieldOp.operator(), connector));
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

    private record PrefixDef(String prefix, QueryKind kind) {}

    private record ParsedSegment(String field, String operator, String connector, int nextIndex) {}

    /** The kind of derived query, derived from the method-name prefix. */
    public enum QueryKind {
        FIND,
        COUNT,
        EXISTS,
        DELETE
    }

    public record ParsedQuery(
            QueryKind kind,
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
