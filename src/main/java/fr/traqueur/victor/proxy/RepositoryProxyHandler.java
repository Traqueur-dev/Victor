package fr.traqueur.victor.proxy;

import fr.traqueur.victor.database.query.DynamicQuerySqlGenerator;
import fr.traqueur.victor.database.query.MethodNameParser;
import fr.traqueur.victor.database.query.QueryAnnotationParser;
import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.Query;
import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.entities.metadata.FieldMetadata;
import fr.traqueur.victor.registries.EntityMetadataRegistry;
import fr.traqueur.victor.reflections.TypeResolver;
import fr.traqueur.victor.database.SqlExecutor;
import fr.traqueur.victor.entities.dialect.Dialect;
import fr.traqueur.victor.database.DtoMapper;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.utils.VictorLogger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

public class RepositoryProxyHandler<DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID>
        implements InvocationHandler {

    /**
     * SQL keywords that should not be quoted in SQL queries.
     */
    private static final Set<String> SQL_KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "ORDER", "BY", "GROUP",
            "HAVING", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON",
            "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "AS", "ASC", "DESC", "LIMIT", "OFFSET", "COUNT", "SUM", "AVG",
            "MIN", "MAX", "DISTINCT", "NULL", "NOT", "IS", "IN", "BETWEEN",
            "LIKE", "EXISTS", "CASE", "WHEN", "THEN", "ELSE", "END"
    );

    private final Class<DTO> dtoClass;
    private final Class<ID> idClass;
    private final EntityMetadata entityMetadata;
    private final SqlExecutor sqlExecutor;
    private final DynamicQuerySqlGenerator sqlGenerator;
    private final Dialect dialect;

    public RepositoryProxyHandler(Class<? extends Repository<DTO,MODEL,ID>> repositoryInterface, SqlExecutor sqlExecutor, Dialect dialect) {
        var typeInfo = TypeResolver.resolveRepositoryTypes(repositoryInterface);
        this.dtoClass = typeInfo.dtoClass();
        Class<MODEL> modelClass = typeInfo.modelClass();
        this.idClass = typeInfo.idClass();
        this.entityMetadata = EntityMetadataRegistry.getInstance().getMetadata(modelClass);
        this.sqlExecutor = sqlExecutor;
        this.dialect = dialect;
         this.sqlGenerator = new DynamicQuerySqlGenerator(
                entityMetadata,
                dialect,
                sqlExecutor.isShowSql()
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        String methodName = method.getName();

        QueryAnnotationParser.ParsedQueryAnnotation parsedQuery = QueryAnnotationParser.parse(method);
        if (parsedQuery != null) {
            return handleCustomQuery(method, args, parsedQuery);
        }

        return switch (methodName) {
            case "save" -> save((DTO) args[0]);
            case "findById" -> findById((ID) args[0]);
            case "findAll" -> findAll();
            case "deleteById" -> { deleteById((ID) args[0]); yield null; }
            case "delete" -> { delete((DTO) args[0]); yield null; }
            case "existsById" -> existsById((ID) args[0]);
            case "count" -> count();
            case "saveAll" -> saveAll((Collection<DTO>) args[0]);
            case "deleteAll" -> {
                if (args.length == 0) deleteAll();
                else deleteAll((Collection<DTO>) args[0]);
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

    /**
     * Préprocesse la query SQL pour ajouter les quotes sur les identifiants.
     *
     * @param sql La query SQL brute
     * @return La query avec les identifiants quotés selon le dialecte
     */
    private String preprocessSqlQuery(String sql) {
        Pattern identifierPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");
        Matcher matcher = identifierPattern.matcher(sql);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String identifier = matcher.group(1);
            String upperIdentifier = identifier.toUpperCase();

            // Si c'est un keyword SQL ou un nombre, ne pas quoter
            if (SQL_KEYWORDS.contains(upperIdentifier) || identifier.matches("\\d+")) {
                matcher.appendReplacement(result, identifier);
            } else {
                // Quoter avec le dialecte
                matcher.appendReplacement(result, dialect.quoteIdentifier(identifier));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private Object handleCustomQuery(Method method, Object[] args, QueryAnnotationParser.ParsedQueryAnnotation parsedQuery) {
        try {
            String sql = parsedQuery.toJdbcQuery();

            sql = preprocessSqlQuery(sql);

            Object[] params = QueryAnnotationParser.mapParameterValues(method, args, parsedQuery.namedParameters());

            if (sqlExecutor.isShowSql()) {
                VictorLogger.debug("Custom Query SQL: {}", sql);
                if (parsedQuery.hasNamedParameters()) {
                    VictorLogger.debug("  Named parameters: {}", parsedQuery.namedParameters());
                }
            }

            // Exécuter selon le type de query
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
        SqlExecutor.RowMapper<DTO> mapper = DtoMapper.createMapper(dtoClass, entityMetadata, sqlExecutor);

        if (returnType == Optional.class) {
            DTO result = sqlExecutor.executeQuerySingle(sql, params, mapper);
            return Optional.ofNullable(result);
        }

        if (List.class.isAssignableFrom(returnType)) {
            return sqlExecutor.executeQuery(sql, params, mapper);
        }

        if (returnType.isAssignableFrom(dtoClass)) {
            return sqlExecutor.executeQuerySingle(sql, params, mapper);
        }

        throw new VictorException(
                "Unsupported return type for @Query method: " + returnType +
                        ". Supported types: Optional<DTO>, List<DTO>, DTO"
        );
    }

    private DTO save(DTO dto) {
        try {
            MODEL model = dto.toModel();

            if (shouldUseSimpleInsert(model)) {
                return insert(dto, model);
            } else {
                return upsert(dto, model);
            }

        } catch (Exception e) {
            throw new VictorException("Failed to save entity: " + dto, e);
        }
    }

    private boolean shouldUseSimpleInsert(MODEL model) {
        return entityMetadata.getIdField().isAutoGenerated() && model.isNew();
    }

    private DTO insert(DTO dto, MODEL model) {
        String sql = dialect.generateInsert(entityMetadata);
        Object[] params = extractNonIdFieldValues(model);

        if (entityMetadata.getIdField().isAutoGenerated()) {
            ID generatedId = sqlExecutor.executeInsertWithGeneratedKey(sql, params, idClass);
            model.setId(generatedId);
            return createDtoFromModel(model);
        } else {
            sqlExecutor.executeUpdate(sql, params);
            return dto;
        }
    }

    private DTO upsert(DTO dto, MODEL model) {
        String sql = dialect.generateUpsert(entityMetadata);
        Object[] params = extractAllFieldValuesWithId(model);

        int rowsAffected = sqlExecutor.executeUpdate(sql, params);
        if (rowsAffected == 0) {
            throw new VictorException("UPSERT failed, no rows affected: " + model.getId());
        }

        return dto;
    }

    private Optional<DTO> findById(ID id) {
        String sql = dialect.generateSelectById(entityMetadata);
        Object[] params = { id };

        SqlExecutor.RowMapper<DTO> mapper = DtoMapper.createMapper(dtoClass, entityMetadata, sqlExecutor);
        DTO result = sqlExecutor.executeQuerySingle(sql, params, mapper);

        return Optional.ofNullable(result);
    }

    private List<DTO> findAll() {
        String sql = dialect.generateSelectAll(entityMetadata);
        SqlExecutor.RowMapper<DTO> mapper = DtoMapper.createMapper(dtoClass, entityMetadata, sqlExecutor);
        return sqlExecutor.executeQuery(sql, null, mapper);
    }

    private void deleteById(ID id) {
        String sql = dialect.generateDelete(entityMetadata);
        Object[] params = { id };

        int rowsAffected = sqlExecutor.executeUpdate(sql, params);
        if (rowsAffected == 0) {
            throw new VictorException("Entity not found for deletion: " + id);
        }
    }

    private void delete(DTO dto) {
        MODEL model = dto.toModel();
        if (model.getId() == null) {
            throw new VictorException("Cannot delete entity without ID");
        }
        deleteById(model.getId());
    }

    private boolean existsById(ID id) {
        String sql = dialect.generateExists(entityMetadata);
        Object[] params = { id };

        long count = sqlExecutor.executeCount(sql, params);
        return count > 0;
    }

    private long count() {
        String sql = dialect.generateCount(entityMetadata);
        return sqlExecutor.executeCount(sql, null);
    }

    /**
     * Saves all entities using JDBC batch operations for improved performance.
     * Falls back to individual saves if batch processing fails.
     */
    private List<DTO> saveAll(Collection<DTO> dtos) {
        List<DTO> dtoList = dtos.stream().toList();

        if (dtoList.isEmpty()) {
            return List.of();
        }

        // Check if all DTOs can use the same batch strategy (all inserts or all upserts)
        boolean allNew = dtoList.stream().allMatch(dto -> dto.toModel().isNew());
        boolean allExisting = dtoList.stream().noneMatch(dto -> dto.toModel().isNew());

        if (entityMetadata.getIdField().isAutoGenerated() && allNew) {
            return batchInsert(dtoList);
        } else if (allExisting) {
            return batchUpsert(dtoList);
        } else {
            // Mixed new/existing entities - fallback to individual saves
            return dtoList.stream().map(this::save).toList();
        }
    }

    /**
     * Performs batch insert operations for new entities with auto-generated IDs.
     * Currently falls back to individual inserts - full batch support requires
     * connection access refactoring.
     */
    private List<DTO> batchInsert(List<DTO> dtos) {
        // TODO: Implement true JDBC batch operations when connection access is available
        return dtos.stream().map(this::save).toList();
    }

    /**
     * Performs batch upsert operations for existing entities.
     * Currently falls back to individual upserts - full batch support requires
     * connection access refactoring.
     */
    private List<DTO> batchUpsert(List<DTO> dtos) {
        // TODO: Implement true JDBC batch operations when connection access is available
        return dtos.stream().map(this::save).toList();
    }

    private void deleteAll(Collection<DTO> dtos) {
        dtos.forEach(this::delete);
    }

    private void deleteAll() {
        String tableName = dialect.quoteIdentifier(entityMetadata.getTableName());
        if (entityMetadata.getSchema() != null) {
            tableName = dialect.quoteIdentifier(entityMetadata.getSchema()) + "." + tableName;
        }
        String sql = "DELETE FROM " + tableName;
        sqlExecutor.executeUpdate(sql, null);
    }

    private Query<DTO> query() {
        return new QueryProxyHandler<>(dtoClass, entityMetadata, sqlExecutor, dialect).createProxy();
    }

    /**
     * Gère les méthodes de query dynamiques (findByUsername, findByEmailAndActive, etc.).
     */
    private Object handleDynamicFinderMethod(Method method, Object[] args) {
        try {
            MethodNameParser.ParsedQuery parsedQuery = MethodNameParser.parse(method);

            String sql = sqlGenerator.generateSql(parsedQuery);

            Object[] preparedArgs = prepareArguments(parsedQuery, args);

            SqlExecutor.RowMapper<DTO> mapper = DtoMapper.createMapper(dtoClass, entityMetadata, sqlExecutor);

            Class<?> returnType = method.getReturnType();

            if (returnType == Optional.class) {
                DTO result = sqlExecutor.executeQuerySingle(sql, preparedArgs, mapper);
                return Optional.ofNullable(result);

            } else if (List.class.isAssignableFrom(returnType)) {
                return sqlExecutor.executeQuery(sql, preparedArgs, mapper);

            } else if (returnType.isAssignableFrom(dtoClass)) {
                return sqlExecutor.executeQuerySingle(sql, preparedArgs, mapper);

            } else {
                throw new VictorException("Unsupported return type for dynamic query method: " + returnType + ". Supported types: Optional<DTO>, List<DTO>, DTO");
            }

        } catch (Exception e) {
            throw new VictorException("Failed to execute dynamic query method: " + method.getName(), e);
        }
    }

    private Object[] prepareArguments(MethodNameParser.ParsedQuery parsedQuery, Object[] args) {
        if (args == null || args.length == 0) {
            return args;
        }

        Object[] prepared = new Object[args.length];
        int argIndex = 0;

        for (MethodNameParser.WhereCondition condition : parsedQuery.conditions()) {
            if (!condition.requiresParameter()) {
                continue;  // IsNull, IsNotNull n'ont pas de paramètre
            }

            if (argIndex >= args.length) {
                throw new VictorException(
                        "Not enough arguments provided for query. Expected at least " +
                                (argIndex + 1) + " but got " + args.length
                );
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

    // ========== Helper Methods ==========

    private Object[] extractNonIdFieldValues(MODEL model) {
        return entityMetadata.getNonIdFields().stream()
                .map(field -> field.getValue(model))
                .toArray();
    }

    private Object[] extractAllFieldValues(MODEL model) {
        var nonIdValues = entityMetadata.getNonIdFields().stream()
                .map(field -> field.getValue(model))
                .toList();

        var allValues = new java.util.ArrayList<>(nonIdValues);
        allValues.add(entityMetadata.getIdField().getValue(model));

        return allValues.toArray();
    }

    private Object[] extractAllFieldValuesWithId(MODEL model) {
        return entityMetadata.getFields().stream()
                .map(field -> field.getValue(model))
                .toArray();
    }

    private DTO createDtoFromModel(MODEL model) {
        try {
            if (dtoClass.isRecord()) {
                return createRecordFromModel(model);
            } else {
                return createClassFromModel(model);
            }
        } catch (Exception e) {
            throw new VictorException("Failed to create DTO from model", e);
        }
    }

    private DTO createRecordFromModel(MODEL model) throws Exception {
        var components = dtoClass.getRecordComponents();
        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            String componentName = components[i].getName();

            FieldMetadata field = findFieldByName(componentName);
            if (field != null) {
                args[i] = field.getValue(model);
            }
        }

        var constructor = dtoClass.getDeclaredConstructor(
                java.util.Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class[]::new)
        );

        return constructor.newInstance(args);
    }

    private DTO createClassFromModel(MODEL model) throws Exception {
        DTO dto = dtoClass.getDeclaredConstructor().newInstance();

        var fields = dtoClass.getDeclaredFields();
        for (var field : fields) {
            field.setAccessible(true);

            FieldMetadata entityField = findFieldByName(field.getName());
            if (entityField != null) {
                Object value = entityField.getValue(model);
                field.set(dto, value);
            }
        }

        return dto;
    }

    private FieldMetadata findFieldByName(String name) {
        return entityMetadata.getFields().stream()
                .filter(f -> f.getField().getName().equals(name) || f.getColumnName().equals(name))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    public static <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID, T extends Repository<DTO,MODEL,ID>> T createProxy(Class<T> repositoryInterface, SqlExecutor sqlExecutor, Dialect dialect) {
        var handler = new RepositoryProxyHandler<>(repositoryInterface, sqlExecutor, dialect);
        return (T) Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class[]{repositoryInterface},
                handler
        );
    }
}