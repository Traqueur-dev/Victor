package fr.traqueur.victor.database.query;

import fr.traqueur.victor.annotations.Query;
import fr.traqueur.victor.exceptions.VictorException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class QueryAnnotationParser {

    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

    private QueryAnnotationParser() {}

    public static ParsedQueryAnnotation parse(Method method) {
        Query queryAnnotation = method.getAnnotation(Query.class);
        if (queryAnnotation == null) {
            return null;
        }

        String queryString = queryAnnotation.value().trim();
        if (queryString.isEmpty()) {
            throw new VictorException("@Query value cannot be empty for method: " + method.getName());
        }

        QueryType queryType = determineQueryType(queryString);


        List<String> namedParameters = extractNamedParameters(queryString);

        int positionalParamCount = countPositionalParameters(queryString);

        if (!namedParameters.isEmpty() && positionalParamCount > 0) {
            throw new VictorException(
                    "Cannot mix named parameters (:name) and positional parameters (?) in method: " + method.getName()
            );
        }

        int expectedParamCount = namedParameters.isEmpty() ? positionalParamCount : namedParameters.size();
        int actualParamCount = method.getParameterCount();

        if (expectedParamCount != actualParamCount) {
            throw new VictorException(
                    String.format("Method %s expects %d parameter(s) but query has %d parameter(s)",
                            method.getName(), actualParamCount, expectedParamCount)
            );
        }

        return new ParsedQueryAnnotation(
                queryString,
                queryType,
                namedParameters,
                positionalParamCount
        );
    }

    private static QueryType determineQueryType(String query) {
        String upperQuery = query.trim().toUpperCase();

        if (upperQuery.startsWith("SELECT")) {
            if (upperQuery.matches("^SELECT\\s+COUNT\\s*\\(.*")) {
                return QueryType.COUNT;
            }
            return QueryType.SELECT;
        } else if (upperQuery.startsWith("UPDATE")) {
            return QueryType.UPDATE;
        } else if (upperQuery.startsWith("DELETE")) {
            return QueryType.DELETE;
        } else if (upperQuery.startsWith("INSERT")) {
            return QueryType.INSERT;
        }

        throw new VictorException("Unsupported query type: " + query);
    }

    private static List<String> extractNamedParameters(String query) {
        List<String> params = new ArrayList<>();
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(query);

        while (matcher.find()) {
            String paramName = matcher.group(1);
            params.add(paramName);
        }

        return params;
    }

    private static int countPositionalParameters(String query) {
        int count = 0;
        for (char c : query.toCharArray()) {
            if (c == '?') {
                count++;
            }
        }
        return count;
    }

    public static String replaceNamedParametersWithPositional(String query) {
        return NAMED_PARAM_PATTERN.matcher(query).replaceAll("?");
    }

    public static Object[] mapParameterValues(Method method, Object[] args, List<String> namedParameters) {
        if (namedParameters.isEmpty()) {
            return args;
        }

        // Paramètres nommés : il faut mapper
        String[] paramNames = new String[method.getParameterCount()];
        var parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            paramNames[i] = parameters[i].getName();
        }

        // Créer le tableau de valeurs dans l'ordre des paramètres nommés
        Object[] orderedValues = new Object[namedParameters.size()];
        for (int i = 0; i < namedParameters.size(); i++) {
            String namedParam = namedParameters.get(i);

            // Trouver l'index du paramètre dans la méthode
            int paramIndex = -1;
            for (int j = 0; j < paramNames.length; j++) {
                if (paramNames[j].equals(namedParam)) {
                    paramIndex = j;
                    break;
                }
            }

            if (paramIndex == -1) {
                throw new VictorException(
                        "Named parameter :" + namedParam + " not found in method " + method.getName() +
                                ". Available parameters: " + String.join(", ", paramNames)
                );
            }

            orderedValues[i] = args[paramIndex];
        }

        return orderedValues;
    }

    /**
     * Type de query.
     */
    public enum QueryType {
        SELECT,
        COUNT,
        UPDATE,
        DELETE,
        INSERT
    }

    /**
     * Résultat du parsing d'une annotation @Query.
     */
    public record ParsedQueryAnnotation(
            String queryString,
            QueryType queryType,
            List<String> namedParameters,
            int positionalParameterCount
    ) {
        /**
         * Vérifie si la query utilise des paramètres nommés.
         */
        public boolean hasNamedParameters() {
            return !namedParameters.isEmpty();
        }

        /**
         * Retourne la query SQL prête pour JDBC (avec ? au lieu de :name).
         */
        public String toJdbcQuery() {
            if (hasNamedParameters()) {
                return QueryAnnotationParser.replaceNamedParametersWithPositional(queryString);
            }
            return queryString;
        }
    }
}