package fr.traqueur.victor.proxy;

import fr.traqueur.victor.database.SqlExecutor;
import fr.traqueur.victor.database.SqlGenerator;
import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.Query;
import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.database.DtoMapper;
import fr.traqueur.victor.exceptions.VictorException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QueryProxyHandler<DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID> implements InvocationHandler {

    private final Class<DTO> dtoClass;
    private final EntityMetadata entityMetadata;
    private final SqlExecutor sqlExecutor;
    private final QueryBuilder queryBuilder;

    public QueryProxyHandler(Class<DTO> dtoClass, EntityMetadata entityMetadata,
                             SqlExecutor sqlExecutor) {
        this.dtoClass = dtoClass;
        this.entityMetadata = entityMetadata;
        this.sqlExecutor = sqlExecutor;
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
                queryBuilder.where((String) args[0], getVarArgs(args));
                yield proxy;
            }
            case "and" -> {
                queryBuilder.and((String) args[0], getVarArgs(args));
                yield proxy;
            }
            case "or" -> {
                queryBuilder.or((String) args[0], getVarArgs(args));
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
                queryBuilder.having((String) args[0], getVarArgs(args));
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

    private Object[] getVarArgs(Object[] args) {
        if (args == null || args.length <= 1) {
            return new Object[0];
        }
        Object[] result = new Object[args.length - 1];
        System.arraycopy(args, 1, result, 0, result.length);
        return result;
    }

    private List<DTO> findAll() {
        String sql = queryBuilder.build();
        Object[] params = queryBuilder.getParameters();
        SqlExecutor.RowMapper<DTO> mapper = DtoMapper.createMapper(dtoClass, entityMetadata, sqlExecutor);
        return sqlExecutor.executeQuery(sql, params, mapper);
    }

    private Optional<DTO> findOne() {
        String sql = queryBuilder.copy().limit(1).build();
        Object[] params = queryBuilder.getParameters();

        SqlExecutor.RowMapper<DTO> mapper = DtoMapper.createMapper(dtoClass, entityMetadata, sqlExecutor);
        DTO result = sqlExecutor.executeQuerySingle(sql, params, mapper);
        return Optional.ofNullable(result);
    }

    private DTO findFirst() {
        return findOne().orElseThrow(() ->
                new VictorException("No entity found"));
    }

    private long count() {
        String sql = queryBuilder.buildCount();
        Object[] params = queryBuilder.getParameters();

        return sqlExecutor.executeCount(sql, params);
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
        private final List<Object> parameters = new ArrayList<>();
        private final List<String> joins = new ArrayList<>();
        private final List<String> orderByColumns = new ArrayList<>();
        private final List<String> groupByColumns = new ArrayList<>();
        private final List<String> havingConditions = new ArrayList<>();
        private Integer limitValue;
        private Integer offsetValue;

        public QueryBuilder(EntityMetadata entityMetadata) {
            this.entityMetadata = entityMetadata;
        }

        public void select(String[] columns) {
            selectColumns.clear();
            selectColumns.addAll(List.of(columns));
        }

        public void where(String condition, Object[] params) {
            whereConditions.clear();
            parameters.clear();
            whereConditions.add(condition);
            if (params != null) {
                parameters.addAll(List.of(params));
            }
        }

        public void and(String condition, Object[] params) {
            whereConditions.add("AND " + condition);
            if (params != null) {
                parameters.addAll(List.of(params));
            }
        }

        public void or(String condition, Object[] params) {
            whereConditions.add("OR " + condition);
            if (params != null) {
                parameters.addAll(List.of(params));
            }
        }

        public void join(String table, String condition) {
            joins.add("INNER JOIN " + table + " ON " + condition);
        }

        public void leftJoin(String table, String condition) {
            joins.add("LEFT JOIN " + table + " ON " + condition);
        }

        public void rightJoin(String table, String condition) {
            joins.add("RIGHT JOIN " + table + " ON " + condition);
        }

        public void orderBy(String column, Query.Order order) {
            orderByColumns.add(column + " " + order);
        }

        public QueryBuilder limit(int limit) {
            this.limitValue = limit;
            return this;
        }

        public void offset(int offset) {
            this.offsetValue = offset;
        }

        public void groupBy(String[] columns) {
            groupByColumns.clear();
            groupByColumns.addAll(List.of(columns));
        }

        public void having(String condition, Object[] params) {
            havingConditions.add(condition);
            if (params != null) {
                parameters.addAll(List.of(params));
            }
        }

        public Object[] getParameters() {
            return parameters.toArray();
        }

        public QueryBuilder copy() {
            QueryBuilder copy = new QueryBuilder(this.entityMetadata);
            copy.selectColumns.addAll(this.selectColumns);
            copy.whereConditions.addAll(this.whereConditions);
            copy.parameters.addAll(this.parameters);
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