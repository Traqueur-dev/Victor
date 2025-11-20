package fr.traqueur.victor.proxy;

import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Query;
import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.exceptions.VictorException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QueryProxyHandler<DTO extends Dto<?>> implements InvocationHandler {

    private final Class<DTO> dtoClass;
    private final EntityMetadata entityMetadata;
    private final QueryBuilder queryBuilder;

    public QueryProxyHandler(Class<DTO> dtoClass, EntityMetadata entityMetadata) {
        this.dtoClass = dtoClass;
        this.entityMetadata = entityMetadata;
        this.queryBuilder = new QueryBuilder(entityMetadata);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        return switch (methodName) {
            case "select" -> {
                queryBuilder.select((String[]) args[0]);
                yield proxy; // Return the same proxy instance
            }
            case "where" -> {
                queryBuilder.where((String) args[0], getVarArgs(args, 1));
                yield proxy;
            }
            case "and" -> {
                queryBuilder.and((String) args[0], getVarArgs(args, 1));
                yield proxy;
            }
            case "or" -> {
                queryBuilder.or((String) args[0], getVarArgs(args, 1));
                yield proxy;
            }
            case "join" -> {
                queryBuilder.join((String) args[0], (String) args[1]);
                yield proxy;
            }
            case "leftJoin" -> {
                queryBuilder.leftJoin((String) args[0], (String) args[1]);
                yield proxy;
            }
            case "rightJoin" -> {
                queryBuilder.rightJoin((String) args[0], (String) args[1]);
                yield proxy;
            }
            case "orderBy" -> {
                queryBuilder.orderBy((String) args[0], (Query.Order) args[1]);
                yield proxy;
            }
            case "orderByAsc" -> {
                queryBuilder.orderBy((String) args[0], Query.Order.ASC);
                yield proxy;
            }
            case "orderByDesc" -> {
                queryBuilder.orderBy((String) args[0], Query.Order.DESC);
                yield proxy;
            }
            case "limit" -> {
                queryBuilder.limit((Integer) args[0]);
                yield proxy;
            }
            case "offset" -> {
                queryBuilder.offset((Integer) args[0]);
                yield proxy;
            }
            case "groupBy" -> {
                queryBuilder.groupBy((String[]) args[0]);
                yield proxy;
            }
            case "having" -> {
                queryBuilder.having((String) args[0], getVarArgs(args, 1));
                yield proxy;
            }
            case "findAll" -> findAll();
            case "findOne" -> findOne();
            case "findFirst" -> findFirst();
            case "count" -> count();
            case "exists" -> exists();
            default -> throw new VictorException("Unsupported query method: " + methodName);
        };
    }

    private Object[] getVarArgs(Object[] args, int startIndex) {
        if (args == null || args.length <= startIndex) {
            return new Object[0];
        }
        Object[] result = new Object[args.length - startIndex];
        System.arraycopy(args, startIndex, result, 0, result.length);
        return result;
    }

    private List<DTO> findAll() {
        String sql = queryBuilder.build();
        // TODO: Execute SQL and convert results to DTOs
        System.out.println("Executing query: " + sql);
        return List.of();
    }

    private Optional<DTO> findOne() {
        String sql = queryBuilder.copy().limit(1).build();
        // TODO: Execute SQL and convert result to DTO
        System.out.println("Executing single query: " + sql);
        return Optional.empty();
    }

    private DTO findFirst() {
        return findOne().orElseThrow(() ->
                new VictorException("No entity found"));
    }

    private long count() {
        String sql = queryBuilder.buildCount();
        // TODO: Execute count query
        System.out.println("Executing count query: " + sql);
        return 0L;
    }

    private boolean exists() {
        return count() > 0;
    }

    @SuppressWarnings("unchecked")
    public Query<DTO> createProxy() {
        return (Query<DTO>) Proxy.newProxyInstance(
                Query.class.getClassLoader(),
                new Class[]{Query.class},
                this
        );
    }

    // Inner class for building SQL queries
    private static class QueryBuilder {
        private final EntityMetadata entityMetadata;
        private final List<String> selectColumns = new ArrayList<>();
        private final List<String> whereConditions = new ArrayList<>();
        private final List<String> joins = new ArrayList<>();
        private final List<String> orderByColumns = new ArrayList<>();
        private final List<String> groupByColumns = new ArrayList<>();
        private final List<String> havingConditions = new ArrayList<>();
        private Integer limitValue;
        private Integer offsetValue;

        public QueryBuilder(EntityMetadata entityMetadata) {
            this.entityMetadata = entityMetadata;
        }

        public QueryBuilder select(String[] columns) {
            selectColumns.clear();
            selectColumns.addAll(List.of(columns));
            return this;
        }

        public QueryBuilder where(String condition, Object[] params) {
            whereConditions.clear();
            whereConditions.add(condition);
            return this;
        }

        public QueryBuilder and(String condition, Object[] params) {
            whereConditions.add("AND " + condition);
            return this;
        }

        public QueryBuilder or(String condition, Object[] params) {
            whereConditions.add("OR " + condition);
            return this;
        }

        public QueryBuilder join(String table, String condition) {
            joins.add("INNER JOIN " + table + " ON " + condition);
            return this;
        }

        public QueryBuilder leftJoin(String table, String condition) {
            joins.add("LEFT JOIN " + table + " ON " + condition);
            return this;
        }

        public QueryBuilder rightJoin(String table, String condition) {
            joins.add("RIGHT JOIN " + table + " ON " + condition);
            return this;
        }

        public QueryBuilder orderBy(String column, Query.Order order) {
            orderByColumns.add(column + " " + order);
            return this;
        }

        public QueryBuilder limit(int limit) {
            this.limitValue = limit;
            return this;
        }

        public QueryBuilder offset(int offset) {
            this.offsetValue = offset;
            return this;
        }

        public QueryBuilder groupBy(String[] columns) {
            groupByColumns.clear();
            groupByColumns.addAll(List.of(columns));
            return this;
        }

        public QueryBuilder having(String condition, Object[] params) {
            havingConditions.add(condition);
            return this;
        }

        public QueryBuilder copy() {
            QueryBuilder copy = new QueryBuilder(this.entityMetadata);
            copy.selectColumns.addAll(this.selectColumns);
            copy.whereConditions.addAll(this.whereConditions);
            copy.joins.addAll(this.joins);
            copy.orderByColumns.addAll(this.orderByColumns);
            copy.groupByColumns.addAll(this.groupByColumns);
            copy.havingConditions.addAll(this.havingConditions);
            copy.limitValue = this.limitValue;
            copy.offsetValue = this.offsetValue;
            return copy;
        }

        public String build() {
            StringBuilder sql = new StringBuilder("SELECT ");

            if (selectColumns.isEmpty()) {
                sql.append("*");
            } else {
                sql.append(String.join(", ", selectColumns));
            }

            sql.append(" FROM ").append(entityMetadata.getFullTableName());

            if (!joins.isEmpty()) {
                sql.append(" ").append(String.join(" ", joins));
            }

            if (!whereConditions.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" ", whereConditions));
            }

            if (!groupByColumns.isEmpty()) {
                sql.append(" GROUP BY ").append(String.join(", ", groupByColumns));
            }

            if (!havingConditions.isEmpty()) {
                sql.append(" HAVING ").append(String.join(" AND ", havingConditions));
            }

            if (!orderByColumns.isEmpty()) {
                sql.append(" ORDER BY ").append(String.join(", ", orderByColumns));
            }

            if (limitValue != null) {
                sql.append(" LIMIT ").append(limitValue);
            }

            if (offsetValue != null) {
                sql.append(" OFFSET ").append(offsetValue);
            }

            return sql.toString();
        }

        public String buildCount() {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ");
            sql.append(entityMetadata.getFullTableName());

            if (!joins.isEmpty()) {
                sql.append(" ").append(String.join(" ", joins));
            }

            if (!whereConditions.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" ", whereConditions));
            }

            return sql.toString();
        }
    }
}