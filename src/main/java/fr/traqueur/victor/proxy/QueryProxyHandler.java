package fr.traqueur.victor.proxy;

import fr.traqueur.victor.database.SqlExecutor;
import fr.traqueur.victor.entity.dialect.Dialect;
import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.entity.Model;
import fr.traqueur.victor.entity.Query;
import fr.traqueur.victor.entity.metadata.EntityMetadata;
import fr.traqueur.victor.database.EntityMapper;
import fr.traqueur.victor.exceptions.VictorException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QueryProxyHandler<E extends Entity<MODEL>, MODEL extends Model<ID>, ID> implements InvocationHandler {

    private final Class<E> entityClass;
    private final EntityMetadata entityMetadata;
    private final SqlExecutor sqlExecutor;
    private final QueryBuilder queryBuilder;

    public QueryProxyHandler(Class<E> entityClass, EntityMetadata entityMetadata,
                             SqlExecutor sqlExecutor, Dialect dialect) {
        this.entityClass = entityClass;
        this.entityMetadata = entityMetadata;
        this.sqlExecutor = sqlExecutor;
        this.queryBuilder = new QueryBuilder(entityMetadata, dialect);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        return switch (methodName) {
            case "select" -> {
                queryBuilder.select((String[]) args[0]);
                yield proxy;
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

    private List<E> findAll() {
        String sql = queryBuilder.build();
        Object[] params = queryBuilder.getParameters();
        SqlExecutor.RowMapper<E> mapper = EntityMapper.createMapper(entityClass, entityMetadata, sqlExecutor);
        return sqlExecutor.executeQuery(sql, params, mapper);
    }

    private Optional<E> findOne() {
        String sql = queryBuilder.copy().limit(1).build();
        Object[] params = queryBuilder.getParameters();

        SqlExecutor.RowMapper<E> mapper = EntityMapper.createMapper(entityClass, entityMetadata, sqlExecutor);
        E result = sqlExecutor.executeQuerySingle(sql, params, mapper);
        return Optional.ofNullable(result);
    }

    private E findFirst() {
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
    public Query<E> createProxy() {
        return (Query<E>) Proxy.newProxyInstance(
                Query.class.getClassLoader(),
                new Class[]{Query.class},
                this
        );
    }

    private static class QueryBuilder {
        private final EntityMetadata entityMetadata;
        private final Dialect dialect;
        private final List<String> selectColumns = new ArrayList<>();
        private final List<String> whereConditions = new ArrayList<>();
        private final List<Object> parameters = new ArrayList<>();
        private final List<String> joins = new ArrayList<>();
        private final List<String> orderByColumns = new ArrayList<>();
        private final List<String> groupByColumns = new ArrayList<>();
        private final List<String> havingConditions = new ArrayList<>();
        private Integer limitValue;
        private Integer offsetValue;

        public QueryBuilder(EntityMetadata entityMetadata, Dialect dialect) {
            this.entityMetadata = entityMetadata;
            this.dialect = dialect;
        }

        public void select(String[] columns) {
            selectColumns.clear();
            for (String column : columns) {
                selectColumns.add(dialect.quoteIdentifier(column));
            }
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
            joins.add("INNER JOIN " + dialect.quoteIdentifier(table) + " ON " + condition);
        }

        public void leftJoin(String table, String condition) {
            joins.add("LEFT JOIN " + dialect.quoteIdentifier(table) + " ON " + condition);
        }

        public void rightJoin(String table, String condition) {
            joins.add("RIGHT JOIN " + dialect.quoteIdentifier(table) + " ON " + condition);
        }

        public void orderBy(String column, Query.Order order) {
            orderByColumns.add(dialect.quoteIdentifier(column) + " " + order);
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
            for (String column : columns) {
                groupByColumns.add(dialect.quoteIdentifier(column));
            }
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
            QueryBuilder copy = new QueryBuilder(this.entityMetadata, this.dialect);
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

            sql.append(" FROM ").append(getQuotedFullTableName());

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
            sql.append(getQuotedFullTableName());

            if (!joins.isEmpty()) {
                sql.append(" ").append(String.join(" ", joins));
            }

            if (!whereConditions.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" ", whereConditions));
            }

            return sql.toString();
        }

        private String getQuotedFullTableName() {
            String tableName = dialect.quoteIdentifier(entityMetadata.getTableName());
            if (entityMetadata.getSchema() != null) {
                return dialect.quoteIdentifier(entityMetadata.getSchema()) + "." + tableName;
            }
            return tableName;
        }
    }
}