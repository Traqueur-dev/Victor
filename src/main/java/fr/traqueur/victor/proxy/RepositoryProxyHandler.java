package fr.traqueur.victor.proxy;

import fr.traqueur.victor.database.query.DynamicQuerySqlGenerator;
import fr.traqueur.victor.database.query.MethodNameParser;
import fr.traqueur.victor.database.query.QueryAnnotationParser;
import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.entity.Model;
import fr.traqueur.victor.entity.Query;
import fr.traqueur.victor.entity.Repository;
import fr.traqueur.victor.entity.metadata.EntityMetadata;
import fr.traqueur.victor.entity.metadata.FieldMetadata;
import fr.traqueur.victor.entity.metadata.RelationshipMetadata;
import fr.traqueur.victor.registries.EntityMetadataRegistry;
import fr.traqueur.victor.reflections.TypeResolver;
import fr.traqueur.victor.database.SqlExecutor;
import fr.traqueur.victor.entity.dialect.Dialect;
import fr.traqueur.victor.entity.transaction.TransactionContext;
import fr.traqueur.victor.managers.TransactionManager;
import fr.traqueur.victor.database.EntityMapper;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.utils.VictorLogger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RepositoryProxyHandler<E extends Entity<MODEL>, MODEL extends Model<ID>, ID>
        implements InvocationHandler {

    private static final Set<String> SQL_KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "ORDER", "BY", "GROUP",
            "HAVING", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON",
            "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "AS", "ASC", "DESC", "LIMIT", "OFFSET", "COUNT", "SUM", "AVG",
            "MIN", "MAX", "DISTINCT", "NULL", "NOT", "IS", "IN", "BETWEEN",
            "LIKE", "EXISTS", "CASE", "WHEN", "THEN", "ELSE", "END"
    );

    private final Class<E> entityClass;
    private final Class<ID> idClass;
    private final EntityMetadata entityMetadata;
    private final SqlExecutor sqlExecutor;
    private final DynamicQuerySqlGenerator sqlGenerator;
    private final Dialect dialect;
    private final TransactionManager transactionManager;

    public RepositoryProxyHandler(Class<? extends Repository<E, MODEL, ID>> repositoryInterface,
                                  SqlExecutor sqlExecutor, Dialect dialect) {
        var typeInfo = TypeResolver.resolveRepositoryTypes(repositoryInterface);
        this.entityClass = typeInfo.entityClass();
        this.idClass = typeInfo.idClass();
        this.entityMetadata = EntityMetadataRegistry.getInstance().getMetadata(entityClass);
        this.sqlExecutor = sqlExecutor;
        this.dialect = dialect;
        this.sqlGenerator = new DynamicQuerySqlGenerator(entityMetadata, dialect, sqlExecutor.isShowSql());
        this.transactionManager = new TransactionManager(sqlExecutor.connectionManager());
    }

    /** Runs the action in a transaction, reusing the current one if already inside a transaction. */
    private <T> T runInTransaction(Supplier<T> action) {
        if (TransactionContext.hasActiveTransaction()) {
            return action.get();
        }
        return transactionManager.executeInTransaction(action::get);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        String methodName = method.getName();

        QueryAnnotationParser.ParsedQueryAnnotation parsedQuery = QueryAnnotationParser.parse(method);
        if (parsedQuery != null) {
            return handleCustomQuery(method, args, parsedQuery);
        }

        return switch (methodName) {
            case "save" -> save((E) args[0]);
            case "findById" -> findById((ID) args[0]);
            case "findAll" -> findAll();
            case "deleteById" -> { deleteById((ID) args[0]); yield null; }
            case "delete" -> { delete((E) args[0]); yield null; }
            case "existsById" -> existsById((ID) args[0]);
            case "count" -> count();
            case "saveAll" -> saveAll((Collection<E>) args[0]);
            case "deleteAll" -> {
                if (args == null || args.length == 0) deleteAll();
                else deleteAll((Collection<E>) args[0]);
                yield null;
            }
            case "query" -> query();
            default -> {
                if (methodName.startsWith("findBy")) {
                    yield handleDynamicFinderMethod(method, args);
                }
                throw new VictorException("Unsupported repository method: " + methodName);
            }
        };
    }

    private String preprocessSqlQuery(String sql) {
        Pattern identifierPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");
        Matcher matcher = identifierPattern.matcher(sql);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String identifier = matcher.group(1);
            String upperIdentifier = identifier.toUpperCase();
            if (SQL_KEYWORDS.contains(upperIdentifier) || identifier.matches("\\d+")) {
                matcher.appendReplacement(result, identifier);
            } else {
                matcher.appendReplacement(result, dialect.quoteIdentifier(identifier));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Object handleCustomQuery(Method method, Object[] args,
                                     QueryAnnotationParser.ParsedQueryAnnotation parsedQuery) {
        try {
            String sql = preprocessSqlQuery(parsedQuery.toJdbcQuery());
            Object[] params = QueryAnnotationParser.mapParameterValues(method, args, parsedQuery.namedParameters());

            if (sqlExecutor.isShowSql()) {
                VictorLogger.debug("Custom Query SQL: {}", sql);
                if (parsedQuery.hasNamedParameters()) {
                    VictorLogger.debug("  Named parameters: {}", parsedQuery.namedParameters());
                }
            }

            return switch (parsedQuery.queryType()) {
                case SELECT -> executeCustomSelect(method, sql, params);
                case COUNT -> sqlExecutor.executeCount(sql, params);
                case UPDATE, DELETE, INSERT -> sqlExecutor.executeUpdate(sql, params);
            };
        } catch (Exception e) {
            throw new VictorException("Failed to execute custom query for method: " + method.getName(), e);
        }
    }

    private Object executeCustomSelect(Method method, String sql, Object[] params) {
        Class<?> returnType = method.getReturnType();
        SqlExecutor.RowMapper<E> mapper = EntityMapper.createMapper(entityClass, entityMetadata, sqlExecutor);

        if (returnType == Optional.class) {
            return Optional.ofNullable(sqlExecutor.executeQuerySingle(sql, params, mapper));
        }
        if (List.class.isAssignableFrom(returnType)) {
            return sqlExecutor.executeQuery(sql, params, mapper);
        }
        if (returnType.isAssignableFrom(entityClass)) {
            return sqlExecutor.executeQuerySingle(sql, params, mapper);
        }
        throw new VictorException(
                "Unsupported return type for @Query method: " + returnType +
                ". Supported types: Optional<E>, List<E>, E");
    }

    private E save(E entity) {
        try {
            if (shouldUseSimpleInsert(entity)) {
                return insert(entity);
            } else {
                return upsert(entity);
            }
        } catch (Exception e) {
            throw new VictorException("Failed to save entity: " + entity, e);
        }
    }

    private boolean shouldUseSimpleInsert(E entity) {
        if (!entityMetadata.getIdField().isAutoGenerated()) {
            return false;
        }
        Object idValue = getEntityIdValue(entity);
        return idValue == null || isDefaultId(idValue);
    }

    private boolean isDefaultId(Object id) {
        if (id instanceof Number num) {
            return num.longValue() == 0L;
        }
        return false;
    }

    private E insert(E entity) {
        String sql = dialect.generateInsert(entityMetadata);
        Object[] params = extractNonIdFieldValues(entity);

        if (entityMetadata.getIdField().isAutoGenerated()) {
            ID generatedId = sqlExecutor.executeInsertWithGeneratedKey(sql, params, idClass);
            E saved = createEntityWithId(entity, generatedId);
            saveManyToManyRelationships(saved, generatedId);
            return saved;
        } else {
            sqlExecutor.executeUpdate(sql, params);
            saveManyToManyRelationships(entity, getEntityIdValue(entity));
            return entity;
        }
    }

    private E upsert(E entity) {
        String sql = dialect.generateUpsert(entityMetadata);
        Object[] params = extractAllFieldValuesWithId(entity);

        int rowsAffected = sqlExecutor.executeUpdate(sql, params);
        if (rowsAffected == 0) {
            throw new VictorException("UPSERT failed, no rows affected: " + getEntityIdValue(entity));
        }
        Object id = getEntityIdValue(entity);
        clearManyToManyRelationships(id);
        saveManyToManyRelationships(entity, id);
        return entity;
    }

    private void saveManyToManyRelationships(E entity, Object currentId) {
        for (RelationshipMetadata rel : entityMetadata.getRelationships()) {
            if (rel.getType() != RelationshipMetadata.RelationType.MANY_TO_MANY) continue;
            Object collection = getEntityFieldValue(entity, rel.getFieldName());
            if (!(collection instanceof Collection<?> items) || items.isEmpty()) continue;
            String insertSql = "INSERT INTO " + dialect.quoteIdentifier(rel.getJoinTable())
                    + " (" + dialect.quoteIdentifier(rel.getJoinColumn())
                    + ", " + dialect.quoteIdentifier(rel.getInverseJoinColumn()) + ")"
                    + " VALUES (?, ?)";
            EntityMetadata targetMeta = EntityMetadataRegistry.getInstance().getMetadata(rel.getTargetEntityClass());
            for (Object item : items) {
                if (item == null) continue;
                Object targetId = targetMeta.getIdField().getValue(item);
                sqlExecutor.executeUpdate(insertSql, new Object[]{currentId, targetId});
            }
        }
    }

    private void clearManyToManyRelationships(Object id) {
        for (RelationshipMetadata rel : entityMetadata.getRelationships()) {
            if (rel.getType() != RelationshipMetadata.RelationType.MANY_TO_MANY) continue;
            String deleteSql = "DELETE FROM " + dialect.quoteIdentifier(rel.getJoinTable())
                    + " WHERE " + dialect.quoteIdentifier(rel.getJoinColumn()) + " = ?";
            sqlExecutor.executeUpdate(deleteSql, new Object[]{id});
        }
    }

    private Optional<E> findById(ID id) {
        String sql = dialect.generateSelectById(entityMetadata);
        SqlExecutor.RowMapper<E> mapper = EntityMapper.createMapper(entityClass, entityMetadata, sqlExecutor);
        return Optional.ofNullable(sqlExecutor.executeQuerySingle(sql, new Object[]{id}, mapper));
    }

    private List<E> findAll() {
        String sql = dialect.generateSelectAll(entityMetadata);
        SqlExecutor.RowMapper<E> mapper = EntityMapper.createMapper(entityClass, entityMetadata, sqlExecutor);
        return sqlExecutor.executeQuery(sql, null, mapper);
    }

    private void deleteById(ID id) {
        // Clean ManyToMany junction tables first
        for (RelationshipMetadata rel : entityMetadata.getRelationships()) {
            if (rel.getType() == RelationshipMetadata.RelationType.MANY_TO_MANY) {
                String deleteSql = "DELETE FROM " + dialect.quoteIdentifier(rel.getJoinTable())
                        + " WHERE " + dialect.quoteIdentifier(rel.getJoinColumn()) + " = ?";
                sqlExecutor.executeUpdate(deleteSql, new Object[]{id});
            }
        }

        String sql = dialect.generateDelete(entityMetadata);
        int rowsAffected = sqlExecutor.executeUpdate(sql, new Object[]{id});
        if (rowsAffected == 0) {
            throw new VictorException("Model not found for deletion: " + id);
        }
    }

    private void delete(E entity) {
        Object idValue = getEntityIdValue(entity);
        if (idValue == null) {
            throw new VictorException("Cannot delete entity without ID");
        }
        @SuppressWarnings("unchecked")
        ID id = (ID) idValue;
        deleteById(id);
    }

    private boolean existsById(ID id) {
        String sql = dialect.generateExists(entityMetadata);
        return sqlExecutor.executeCount(sql, new Object[]{id}) > 0;
    }

    private long count() {
        String sql = dialect.generateCount(entityMetadata);
        return sqlExecutor.executeCount(sql, null);
    }

    private List<E> saveAll(Collection<E> entities) {
        List<E> entityList = entities.stream().toList();
        if (entityList.isEmpty()) return List.of();

        boolean allNew = entityList.stream().allMatch(this::isEntityNew);
        boolean allExisting = entityList.stream().noneMatch(this::isEntityNew);

        // The whole batch (rows + join tables) is persisted atomically.
        return runInTransaction(() -> {
            if (entityMetadata.getIdField().isAutoGenerated() && allNew) {
                return batchInsert(entityList);
            } else if (allExisting) {
                return batchUpsert(entityList);
            } else {
                return entityList.stream().map(this::save).toList();
            }
        });
    }

    private boolean isEntityNew(E entity) {
        Object idValue = getEntityIdValue(entity);
        return idValue == null || isDefaultId(idValue);
    }

    /**
     * Batch insert of new entities with auto-generated ids using JDBC addBatch/executeBatch.
     * Only reached when the id is auto-generated (see {@link #saveAll}).
     */
    private List<E> batchInsert(List<E> entities) {
        String sql = dialect.generateInsert(entityMetadata);
        List<Object[]> batchParams = entities.stream()
                .map(this::extractNonIdFieldValues)
                .toList();

        List<ID> generatedIds = sqlExecutor.executeBatchInsertWithGeneratedKeys(sql, batchParams, idClass);

        // Defensive: if the driver did not return one key per row, fall back to per-row save.
        if (generatedIds.size() != entities.size()) {
            return entities.stream().map(this::save).toList();
        }

        List<E> saved = new ArrayList<>(entities.size());
        for (int i = 0; i < entities.size(); i++) {
            E withId = createEntityWithId(entities.get(i), generatedIds.get(i));
            saveManyToManyRelationships(withId, getEntityIdValue(withId));
            saved.add(withId);
        }
        return saved;
    }

    /** Batch upsert of existing entities using JDBC addBatch/executeBatch. */
    private List<E> batchUpsert(List<E> entities) {
        String sql = dialect.generateUpsert(entityMetadata);
        List<Object[]> batchParams = entities.stream()
                .map(this::extractAllFieldValuesWithId)
                .toList();

        sqlExecutor.executeBatch(sql, batchParams);

        for (E entity : entities) {
            Object id = getEntityIdValue(entity);
            clearManyToManyRelationships(id);
            saveManyToManyRelationships(entity, id);
        }
        return entities;
    }

    private void deleteAll(Collection<E> entities) {
        entities.forEach(this::delete);
    }

    private void deleteAll() {
        String tableName = dialect.getFullTableName(entityMetadata);
        sqlExecutor.executeUpdate("DELETE FROM " + tableName, null);
    }

    private Query<E> query() {
        return new QueryProxyHandler<>(entityClass, entityMetadata, sqlExecutor, dialect).createProxy();
    }

    private Object handleDynamicFinderMethod(Method method, Object[] args) {
        try {
            MethodNameParser.ParsedQuery parsedQuery = MethodNameParser.parse(method);
            String sql = sqlGenerator.generateSql(parsedQuery);
            Object[] preparedArgs = prepareArguments(parsedQuery, args);
            SqlExecutor.RowMapper<E> mapper = EntityMapper.createMapper(entityClass, entityMetadata, sqlExecutor);

            Class<?> returnType = method.getReturnType();
            if (returnType == Optional.class) {
                return Optional.ofNullable(sqlExecutor.executeQuerySingle(sql, preparedArgs, mapper));
            } else if (List.class.isAssignableFrom(returnType)) {
                return sqlExecutor.executeQuery(sql, preparedArgs, mapper);
            } else if (returnType.isAssignableFrom(entityClass)) {
                return sqlExecutor.executeQuerySingle(sql, preparedArgs, mapper);
            } else {
                throw new VictorException("Unsupported return type for dynamic query: " + returnType);
            }
        } catch (Exception e) {
            throw new VictorException("Failed to execute dynamic query: " + method.getName(), e);
        }
    }

    private Object[] prepareArguments(MethodNameParser.ParsedQuery parsedQuery, Object[] args) {
        if (args == null || args.length == 0) return args;

        Object[] prepared = new Object[args.length];
        int argIndex = 0;

        for (MethodNameParser.WhereCondition condition : parsedQuery.conditions()) {
            if (!condition.requiresParameter()) continue;
            if (argIndex >= args.length) {
                throw new VictorException("Not enough arguments for query: expected at least " + (argIndex + 1));
            }

            Object arg = args[argIndex];
            if (condition.operator().equals("Like") || condition.operator().equals("NotLike")) {
                if (arg instanceof String str) {
                    if (!str.contains("%") && !str.contains("_")) {
                        arg = "%" + dialect.escapeLikePattern(str) + "%";
                    } else {
                        arg = dialect.escapeLikePattern(str);
                    }
                }
            }
            prepared[argIndex] = arg;
            argIndex++;
        }
        return prepared;
    }

    // ========== Value extraction ==========

    private Object[] extractNonIdFieldValues(E entity) {
        return entityMetadata.getNonIdFields().stream()
                .map(field -> getFieldValueForPersist(entity, field))
                .toArray();
    }

    private Object[] extractAllFieldValuesWithId(E entity) {
        return entityMetadata.getFields().stream()
                .map(field -> getFieldValueForPersist(entity, field))
                .toArray();
    }

    /**
     * Gets the value for a field, handling synthetic FK fields by extracting
     * the related E's ID from the relationship field.
     */
    private Object getFieldValueForPersist(E entity, FieldMetadata field) {
        if (field.isSyntheticFk()) {
            return extractForeignKeyValue(entity, field.getColumnName());
        }
        return getEntityFieldValue(entity, field.getFieldName());
    }

    /**
     * For a synthetic FK column, finds the owning relationship and extracts
     * the related E's ID value.
     */
    private Object extractForeignKeyValue(E entity, String fkColumnName) {
        RelationshipMetadata rel = entityMetadata.getOwningSideRelationships().stream()
                .filter(r -> r.getForeignKeyColumn().equals(fkColumnName))
                .findFirst().orElse(null);
        if (rel == null) return null;

        Object relatedEntity = getEntityFieldValue(entity, rel.getFieldName());
        if (relatedEntity == null) return null;

        EntityMetadata targetMeta = EntityMetadataRegistry.getInstance().getMetadata(rel.getTargetEntityClass());
        return targetMeta.getIdField().getValue(relatedEntity);
    }

    private Object getEntityIdValue(E entity) {
        return getEntityFieldValue(entity, entityMetadata.getIdField().getFieldName());
    }

    private Object getEntityFieldValue(E entity, String fieldName) {
        try {
            if (entityClass.isRecord()) {
                Method accessor = entityClass.getMethod(fieldName);
                return accessor.invoke(entity);
            } else {
                java.lang.reflect.Field entityField = findEntityField(entityClass, fieldName);
                if (entityField != null) {
                    entityField.setAccessible(true);
                    return entityField.get(entity);
                }
                throw new VictorException("Field not found in E: " + fieldName);
            }
        } catch (Exception e) {
            throw new VictorException("Failed to extract field value from E: " + fieldName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private E createEntityWithId(E entity, ID generatedId) {
        String idFieldName = entityMetadata.getIdField().getFieldName();
        try {
            if (entityClass.isRecord()) {
                var components = entityClass.getRecordComponents();
                Object[] args = new Object[components.length];
                Class<?>[] types = new Class<?>[components.length];

                for (int i = 0; i < components.length; i++) {
                    String componentName = components[i].getName();
                    types[i] = components[i].getType();
                    if (componentName.equals(idFieldName)) {
                        args[i] = generatedId;
                    } else {
                        Method accessor = entityClass.getMethod(componentName);
                        args[i] = accessor.invoke(entity);
                    }
                }
                return (E) entityClass.getDeclaredConstructor(types).newInstance(args);
            } else {
                java.lang.reflect.Field entityField = findEntityField(entityClass, idFieldName);
                if (entityField != null) {
                    entityField.setAccessible(true);
                    entityField.set(entity, generatedId);
                }
                return entity;
            }
        } catch (Exception e) {
            throw new VictorException("Failed to create E with generated ID", e);
        }
    }

    private java.lang.reflect.Field findEntityField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <E extends Entity<MODEL>, MODEL extends Model<ID>, ID,
            T extends Repository<E, MODEL, ID>> T createProxy(
            Class<T> repositoryInterface, SqlExecutor sqlExecutor, Dialect dialect) {
        var handler = new RepositoryProxyHandler<>(repositoryInterface, sqlExecutor, dialect);
        return (T) Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class[]{repositoryInterface},
                handler
        );
    }
}