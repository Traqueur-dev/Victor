package fr.traqueur.victor.proxy;

import fr.traqueur.victor.annotations.CascadeType;
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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final Set<String> knownFieldNames;

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
        this.knownFieldNames = entityMetadata.getScalarFields().stream()
                .map(FieldMetadata::getFieldName)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Whether a method name is a derived query (findBy/countBy/existsBy/deleteBy). */
    private static boolean isDerivedQueryMethod(String methodName) {
        return methodName.startsWith("findBy")
                || methodName.startsWith("countBy")
                || methodName.startsWith("existsBy")
                || methodName.startsWith("deleteBy");
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
                if (isDerivedQueryMethod(methodName)) {
                    yield handleDerivedQuery(method, args);
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

    @SuppressWarnings("unchecked")
    private E save(E entity) {
        // Entity row, owning FK, cascaded relations and join rows are persisted atomically.
        return runInTransaction(() -> {
            try {
                return (E) saveEntity(entity, entityMetadata);
            } catch (VictorException e) {
                throw e;
            } catch (Exception e) {
                throw new VictorException("Failed to save entity: " + entity, e);
            }
        });
    }

    /**
     * Full save of any entity given its metadata: cascade-persist owning relations first (so FK
     * columns can be filled), insert-or-upsert this row, then synchronize inverse OneToMany children
     * (upsert present children, insert new ones, remove orphans) — recursively for nested aggregates.
     */
    private Object saveEntity(Object entity, EntityMetadata meta) {
        Object withOwning = cascadePersistOwning(entity, meta);
        Object saved = insertOrUpsert(withOwning, meta);
        cascadeSyncChildren(saved, meta);
        return saved;
    }

    private Object insertOrUpsert(Object entity, EntityMetadata meta) {
        return shouldUseSimpleInsert(entity, meta) ? insert(entity, meta) : upsert(entity, meta);
    }

    private boolean shouldUseSimpleInsert(Object entity, EntityMetadata meta) {
        if (!meta.getIdField().isAutoGenerated()) {
            return false;
        }
        Object idValue = meta.getIdField().getValue(entity);
        return idValue == null || isDefaultId(idValue);
    }

    private boolean isDefaultId(Object id) {
        if (id instanceof Number num) {
            return num.longValue() == 0L;
        }
        return false;
    }

    private Object insert(Object entity, EntityMetadata meta) {
        String sql = dialect.generateInsert(meta);
        Object[] params = meta.getNonIdFields().stream()
                .map(field -> getFieldValueForPersist(entity, meta, field))
                .toArray();

        if (meta.getIdField().isAutoGenerated()) {
            Object generatedId = sqlExecutor.executeInsertWithGeneratedKey(sql, params, meta.getIdField().getJavaType());
            Object saved = withField(entity, meta.getIdField().getFieldName(), generatedId);
            saveManyToManyRelationships(saved, meta, meta.getIdField().getValue(saved));
            return saved;
        } else {
            sqlExecutor.executeUpdate(sql, params);
            saveManyToManyRelationships(entity, meta, meta.getIdField().getValue(entity));
            return entity;
        }
    }

    private Object upsert(Object entity, EntityMetadata meta) {
        String sql = dialect.generateUpsert(meta);
        Object[] params = meta.getFields().stream()
                .map(field -> getFieldValueForPersist(entity, meta, field))
                .toArray();

        // No rows affected on an upsert means "row already existed and no column changed" (MySQL's
        // ON DUPLICATE KEY UPDATE reports 0 in that case) — that is a successful no-op, not a failure.
        sqlExecutor.executeUpdate(sql, params);

        Object id = meta.getIdField().getValue(entity);
        clearManyToManyRelationships(meta, id);
        saveManyToManyRelationships(entity, meta, id);
        return entity;
    }

    private void saveManyToManyRelationships(Object entity, EntityMetadata meta, Object currentId) {
        for (RelationshipMetadata rel : meta.getRelationships()) {
            if (rel.getType() != RelationshipMetadata.RelationType.MANY_TO_MANY) continue;
            Object collection = readField(entity, rel.getFieldName());
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

    private void clearManyToManyRelationships(EntityMetadata meta, Object id) {
        for (RelationshipMetadata rel : meta.getRelationships()) {
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
        // Cascade-delete inverse OneToMany children (bottom-up) so FK constraints are satisfied, then
        // clean this entity's ManyToMany junctions, then delete the row itself.
        cascadeDeleteChildren(entityMetadata, selectIdByIdSql(entityMetadata), new Object[]{id});

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

    /** Subquery selecting the id of {@code meta}'s row(s) matching the top-level id parameter. */
    private String selectIdByIdSql(EntityMetadata meta) {
        String idCol = dialect.quoteIdentifier(meta.getIdField().getColumnName());
        return "SELECT " + idCol + " FROM " + dialect.getFullTableName(meta) + " WHERE " + idCol + " = ?";
    }

    /**
     * Recursively deletes cascaded OneToMany children of {@code parentMeta}, bottom-up, without
     * loading rows: each level deletes children whose FK is in the parent-id subquery, after first
     * deleting their own cascaded children. {@code params} carries the single top-level id parameter
     * referenced by the (possibly nested) {@code parentIdSubquery}.
     */
    private void cascadeDeleteChildren(EntityMetadata parentMeta, String parentIdSubquery, Object[] params) {
        for (RelationshipMetadata rel : parentMeta.getRelationships()) {
            if (rel.getType() != RelationshipMetadata.RelationType.ONE_TO_MANY) continue;
            if (!rel.hasCascade(CascadeType.PERSIST)) continue;

            EntityMetadata childMeta = EntityMetadataRegistry.getInstance().getMetadata(rel.getTargetEntityClass());
            String fkColumn = dialect.quoteIdentifier(childForeignKeyColumn(childMeta, rel));
            String childTable = dialect.getFullTableName(childMeta);
            String childIdCol = dialect.quoteIdentifier(childMeta.getIdField().getColumnName());

            // Grandchildren first: their parents are the child rows selected here.
            String childIdSubquery = "SELECT " + childIdCol + " FROM " + childTable
                    + " WHERE " + fkColumn + " IN (" + parentIdSubquery + ")";
            cascadeDeleteChildren(childMeta, childIdSubquery, params);

            String deleteSql = "DELETE FROM " + childTable + " WHERE " + fkColumn + " IN (" + parentIdSubquery + ")";
            sqlExecutor.executeUpdate(deleteSql, params);
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

        // The whole batch (rows + join tables + cascaded children) is persisted atomically.
        return runInTransaction(() -> {
            // Aggregates with cascaded OneToMany children go through the per-row save path so their
            // children are synchronized (batch DML only writes the parent rows).
            if (hasCascadedChildren(entityMetadata)) {
                return entityList.stream().map(this::save).toList();
            }

            boolean allNew = entityList.stream().allMatch(this::isEntityNew);
            boolean allExisting = entityList.stream().noneMatch(this::isEntityNew);

            if (entityMetadata.getIdField().isAutoGenerated() && allNew) {
                return batchInsert(entityList);
            } else if (allExisting) {
                return batchUpsert(entityList);
            } else {
                return entityList.stream().map(this::save).toList();
            }
        });
    }

    private boolean hasCascadedChildren(EntityMetadata meta) {
        return meta.getRelationships().stream()
                .anyMatch(r -> r.getType() == RelationshipMetadata.RelationType.ONE_TO_MANY
                        && r.hasCascade(CascadeType.PERSIST));
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
            saveManyToManyRelationships(withId, entityMetadata, getEntityIdValue(withId));
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
            clearManyToManyRelationships(entityMetadata, id);
            saveManyToManyRelationships(entity, entityMetadata, id);
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

    private Object handleDerivedQuery(Method method, Object[] args) {
        try {
            MethodNameParser.ParsedQuery parsedQuery = MethodNameParser.parse(method, knownFieldNames);
            String sql = sqlGenerator.generateSql(parsedQuery, args);
            Object[] preparedArgs = prepareArguments(parsedQuery, args);

            return switch (parsedQuery.kind()) {
                case FIND -> executeDerivedFind(method, sql, preparedArgs);
                case COUNT -> coerceCount(method.getReturnType(), sqlExecutor.executeCount(sql, preparedArgs));
                case EXISTS -> sqlExecutor.executeCount(sql, preparedArgs) > 0;
                case DELETE -> runInTransaction(() ->
                        coerceDelete(method.getReturnType(), sqlExecutor.executeUpdate(sql, preparedArgs)));
            };
        } catch (VictorException e) {
            throw e;
        } catch (Exception e) {
            throw new VictorException("Failed to execute derived query: " + method.getName(), e);
        }
    }

    private Object executeDerivedFind(Method method, String sql, Object[] preparedArgs) {
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
    }

    private Object coerceCount(Class<?> returnType, long count) {
        if (returnType == int.class || returnType == Integer.class) {
            return (int) count;
        }
        return count;
    }

    private Object coerceDelete(Class<?> returnType, int rowsAffected) {
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        if (returnType == long.class || returnType == Long.class) {
            return (long) rowsAffected;
        }
        if (returnType == int.class || returnType == Integer.class) {
            return rowsAffected;
        }
        if (returnType == boolean.class || returnType == Boolean.class) {
            return rowsAffected > 0;
        }
        return null;
    }

    /**
     * Flattens method arguments into JDBC positional parameters, in the same
     * condition order used by {@link DynamicQuerySqlGenerator#generateSql}.
     * {@code In}/{@code NotIn} collection arguments are expanded element by
     * element; {@code Like}/{@code NotLike} arguments get wildcard handling.
     */
    private Object[] prepareArguments(MethodNameParser.ParsedQuery parsedQuery, Object[] args) {
        if (args == null || args.length == 0) return args;

        List<Object> prepared = new ArrayList<>();
        int argIndex = 0;

        for (MethodNameParser.WhereCondition condition : parsedQuery.conditions()) {
            if (!condition.requiresParameter()) continue;
            if (argIndex >= args.length) {
                throw new VictorException("Not enough arguments for query: expected at least " + (argIndex + 1));
            }

            Object arg = args[argIndex];
            String operator = condition.operator();

            if (operator.equals("In") || operator.equals("NotIn")) {
                addInArguments(prepared, arg);
            } else if (operator.equals("Like") || operator.equals("NotLike")) {
                prepared.add(prepareLikeArgument(arg));
            } else {
                prepared.add(arg);
            }
            argIndex++;
        }
        return prepared.toArray();
    }

    private void addInArguments(List<Object> prepared, Object arg) {
        if (arg instanceof Collection<?> collection) {
            prepared.addAll(collection);
        } else if (arg != null && arg.getClass().isArray()) {
            int length = Array.getLength(arg);
            for (int i = 0; i < length; i++) {
                prepared.add(Array.get(arg, i));
            }
        } else if (arg != null) {
            prepared.add(arg);
        }
        // null or empty collection -> no parameters (matches the 1=0 / 1=1 SQL)
    }

    private Object prepareLikeArgument(Object arg) {
        if (arg instanceof String str) {
            if (!str.contains("%") && !str.contains("_")) {
                return "%" + dialect.escapeLikePattern(str) + "%";
            }
            return dialect.escapeLikePattern(str);
        }
        return arg;
    }

    // ========== Cascade persistence ==========

    /**
     * Persists related entities on the owning side (ManyToOne / OneToOne owning) whose relationship
     * is annotated with cascade PERSIST and that are new, so their generated id is available when the
     * foreign-key columns are written. Existing owning targets (e.g. an aggregate parent referenced
     * from its child) are left untouched. Returns the (possibly rebuilt) entity carrying the persisted
     * relations.
     */
    private Object cascadePersistOwning(Object entity, EntityMetadata meta) {
        Object result = entity;
        for (RelationshipMetadata rel : meta.getOwningSideRelationships()) {
            if (!rel.hasCascade(CascadeType.PERSIST)) continue;
            Object related = readField(result, rel.getFieldName());
            if (related == null) continue;
            EntityMetadata relatedMeta = EntityMetadataRegistry.getInstance().getMetadata(rel.getTargetEntityClass());
            if (!isNew(related, relatedMeta)) continue;
            Object persisted = saveEntity(related, relatedMeta);
            result = withField(result, rel.getFieldName(), persisted);
        }
        return result;
    }

    /**
     * Synchronizes inverse OneToMany children of {@code parent} for relationships annotated with
     * cascade PERSIST: upserts each present child (insert if new, update if it already exists) wiring
     * its foreign key back to the parent — recursively for nested collections — then deletes the
     * orphans (rows of this parent no longer present in the collection).
     */
    private void cascadeSyncChildren(Object parent, EntityMetadata meta) {
        for (RelationshipMetadata rel : meta.getRelationships()) {
            if (rel.getType() != RelationshipMetadata.RelationType.ONE_TO_MANY) continue;
            if (!rel.hasCascade(CascadeType.PERSIST)) continue;

            EntityMetadata childMeta = EntityMetadataRegistry.getInstance().getMetadata(rel.getTargetEntityClass());
            String fkColumn = childForeignKeyColumn(childMeta, rel);
            Object parentId = meta.getIdField().getValue(parent);

            Object collection = readField(parent, rel.getFieldName());
            List<Object> keptIds = new ArrayList<>();
            if (collection instanceof Collection<?> children) {
                for (Object child : children) {
                    if (child == null) continue;
                    Object childWithParent = withField(child, rel.getMappedByField(), parent);
                    Object saved = saveEntity(childWithParent, childMeta);
                    keptIds.add(childMeta.getIdField().getValue(saved));
                }
            }
            deleteOrphans(childMeta, fkColumn, parentId, keptIds);
        }
    }

    /** FK column on the child table that references the parent, resolved from the mappedBy ManyToOne. */
    private String childForeignKeyColumn(EntityMetadata childMeta, RelationshipMetadata oneToMany) {
        String mappedBy = oneToMany.getMappedByField();
        return childMeta.getRelationships().stream()
                .filter(r -> r.getFieldName().equals(mappedBy))
                .map(RelationshipMetadata::getForeignKeyColumn)
                .findFirst()
                .orElseThrow(() -> new VictorException(
                        "OneToMany mappedBy='" + mappedBy + "' has no matching ManyToOne on "
                                + childMeta.getTableName()));
    }

    /** Deletes child rows of {@code parentId} whose id is not among {@code keptIds} (orphan removal). */
    private void deleteOrphans(EntityMetadata childMeta, String fkColumn, Object parentId, List<Object> keptIds) {
        String table = dialect.getFullTableName(childMeta);
        String fk = dialect.quoteIdentifier(fkColumn);

        if (keptIds.isEmpty()) {
            sqlExecutor.executeUpdate("DELETE FROM " + table + " WHERE " + fk + " = ?", new Object[]{parentId});
            return;
        }

        String idCol = dialect.quoteIdentifier(childMeta.getIdField().getColumnName());
        String placeholders = keptIds.stream().map(x -> "?").collect(Collectors.joining(", "));
        String sql = "DELETE FROM " + table + " WHERE " + fk + " = ? AND " + idCol + " NOT IN (" + placeholders + ")";

        Object[] params = new Object[keptIds.size() + 1];
        params[0] = parentId;
        for (int i = 0; i < keptIds.size(); i++) {
            params[i + 1] = keptIds.get(i);
        }
        sqlExecutor.executeUpdate(sql, params);
    }

    private boolean isNew(Object entity, EntityMetadata meta) {
        Object id = meta.getIdField().getValue(entity);
        return id == null || isDefaultId(id);
    }

    // ========== Value extraction ==========

    private Object[] extractNonIdFieldValues(E entity) {
        return entityMetadata.getNonIdFields().stream()
                .map(field -> getFieldValueForPersist(entity, entityMetadata, field))
                .toArray();
    }

    private Object[] extractAllFieldValuesWithId(E entity) {
        return entityMetadata.getFields().stream()
                .map(field -> getFieldValueForPersist(entity, entityMetadata, field))
                .toArray();
    }

    /**
     * Gets the value for a field, handling synthetic FK fields by extracting
     * the related entity's ID from the relationship field. Embedded sub-fields are
     * read through {@link FieldMetadata#getValue} (parent accessor then sub accessor).
     */
    private Object getFieldValueForPersist(Object entity, EntityMetadata meta, FieldMetadata field) {
        if (field.isSyntheticFk()) {
            return extractForeignKeyValue(entity, meta, field.getColumnName());
        }
        return field.getValue(entity);
    }

    /**
     * For a synthetic FK column, finds the owning relationship and extracts
     * the related entity's ID value.
     */
    private Object extractForeignKeyValue(Object entity, EntityMetadata meta, String fkColumnName) {
        RelationshipMetadata rel = meta.getOwningSideRelationships().stream()
                .filter(r -> r.getForeignKeyColumn().equals(fkColumnName))
                .findFirst().orElse(null);
        if (rel == null) return null;

        Object relatedEntity = readField(entity, rel.getFieldName());
        if (relatedEntity == null) return null;

        EntityMetadata targetMeta = EntityMetadataRegistry.getInstance().getMetadata(rel.getTargetEntityClass());
        return targetMeta.getIdField().getValue(relatedEntity);
    }

    private Object getEntityIdValue(E entity) {
        return readField(entity, entityMetadata.getIdField().getFieldName());
    }

    @SuppressWarnings("unchecked")
    private E createEntityWithId(E entity, ID generatedId) {
        return (E) withField(entity, entityMetadata.getIdField().getFieldName(), generatedId);
    }

    /** Reads a record component by name on an entity (entities are always records). */
    private Object readField(Object entity, String fieldName) {
        Class<?> clazz = entity.getClass();
        try {
            return clazz.getMethod(fieldName).invoke(entity);
        } catch (Exception e) {
            throw new VictorException("Failed to read field '" + fieldName + "' on " + clazz.getSimpleName(), e);
        }
    }

    /**
     * Returns a copy of {@code entity} with {@code fieldName} set to {@code value},
     * rebuilt through the record's canonical constructor (entities are immutable records).
     */
    private Object withField(Object entity, String fieldName, Object value) {
        Class<?> clazz = entity.getClass();
        try {
            var components = clazz.getRecordComponents();
            Object[] args = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                args[i] = components[i].getName().equals(fieldName)
                        ? value
                        : clazz.getMethod(components[i].getName()).invoke(entity);
            }
            return clazz.getDeclaredConstructor(types).newInstance(args);
        } catch (Exception e) {
            throw new VictorException("Failed to set field '" + fieldName + "' on " + clazz.getSimpleName(), e);
        }
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
