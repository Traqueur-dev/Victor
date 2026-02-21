package fr.traqueur.victor.proxy;

import fr.traqueur.victor.database.query.DynamicQuerySqlGenerator;
import fr.traqueur.victor.database.query.MethodNameParser;
import fr.traqueur.victor.database.query.QueryAnnotationParser;
import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.Query;
import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.metadata.DtoMetadata;
import fr.traqueur.victor.entities.metadata.FieldMetadata;
import fr.traqueur.victor.entities.metadata.RelationshipMetadata;
import fr.traqueur.victor.registries.DtoMetadataRegistry;
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

public class RepositoryProxyHandler<DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID>
        implements InvocationHandler {

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
    private final DtoMetadata dtoMetadata;
    private final SqlExecutor sqlExecutor;
    private final DynamicQuerySqlGenerator sqlGenerator;
    private final Dialect dialect;

    public RepositoryProxyHandler(Class<? extends Repository<DTO, MODEL, ID>> repositoryInterface,
                                  SqlExecutor sqlExecutor, Dialect dialect) {
        var typeInfo = TypeResolver.resolveRepositoryTypes(repositoryInterface);
        this.dtoClass = typeInfo.dtoClass();
        this.idClass = typeInfo.idClass();
        this.dtoMetadata = DtoMetadataRegistry.getInstance().getMetadata(dtoClass);
        this.sqlExecutor = sqlExecutor;
        this.dialect = dialect;
        this.sqlGenerator = new DynamicQuerySqlGenerator(dtoMetadata, dialect, sqlExecutor.isShowSql());
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
                if (args == null || args.length == 0) deleteAll();
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
        SqlExecutor.RowMapper<DTO> mapper = DtoMapper.createMapper(dtoClass, dtoMetadata, sqlExecutor);

        if (returnType == Optional.class) {
            return Optional.ofNullable(sqlExecutor.executeQuerySingle(sql, params, mapper));
        }
        if (List.class.isAssignableFrom(returnType)) {
            return sqlExecutor.executeQuery(sql, params, mapper);
        }
        if (returnType.isAssignableFrom(dtoClass)) {
            return sqlExecutor.executeQuerySingle(sql, params, mapper);
        }
        throw new VictorException(
                "Unsupported return type for @Query method: " + returnType +
                ". Supported types: Optional<DTO>, List<DTO>, DTO");
    }

    private DTO save(DTO dto) {
        try {
            if (shouldUseSimpleInsert(dto)) {
                return insert(dto);
            } else {
                return upsert(dto);
            }
        } catch (Exception e) {
            throw new VictorException("Failed to save entity: " + dto, e);
        }
    }

    private boolean shouldUseSimpleInsert(DTO dto) {
        if (!dtoMetadata.getIdField().isAutoGenerated()) {
            return false;
        }
        Object idValue = getDtoIdValue(dto);
        return idValue == null || isDefaultId(idValue);
    }

    private boolean isDefaultId(Object id) {
        if (id instanceof Number num) {
            return num.longValue() == 0L;
        }
        return false;
    }

    private DTO insert(DTO dto) {
        String sql = dialect.generateInsert(dtoMetadata);
        Object[] params = extractNonIdFieldValues(dto);

        if (dtoMetadata.getIdField().isAutoGenerated()) {
            ID generatedId = sqlExecutor.executeInsertWithGeneratedKey(sql, params, idClass);
            return createDtoWithId(dto, generatedId);
        } else {
            sqlExecutor.executeUpdate(sql, params);
            return dto;
        }
    }

    private DTO upsert(DTO dto) {
        String sql = dialect.generateUpsert(dtoMetadata);
        Object[] params = extractAllFieldValuesWithId(dto);

        int rowsAffected = sqlExecutor.executeUpdate(sql, params);
        if (rowsAffected == 0) {
            throw new VictorException("UPSERT failed, no rows affected: " + getDtoIdValue(dto));
        }
        return dto;
    }

    private Optional<DTO> findById(ID id) {
        String sql = dialect.generateSelectById(dtoMetadata);
        SqlExecutor.RowMapper<DTO> mapper = DtoMapper.createMapper(dtoClass, dtoMetadata, sqlExecutor);
        return Optional.ofNullable(sqlExecutor.executeQuerySingle(sql, new Object[]{id}, mapper));
    }

    private List<DTO> findAll() {
        String sql = dialect.generateSelectAll(dtoMetadata);
        SqlExecutor.RowMapper<DTO> mapper = DtoMapper.createMapper(dtoClass, dtoMetadata, sqlExecutor);
        return sqlExecutor.executeQuery(sql, null, mapper);
    }

    private void deleteById(ID id) {
        // Clean ManyToMany junction tables first
        for (RelationshipMetadata rel : dtoMetadata.getRelationships()) {
            if (rel.getType() == RelationshipMetadata.RelationType.MANY_TO_MANY) {
                String deleteSql = "DELETE FROM " + dialect.quoteIdentifier(rel.getJoinTable())
                        + " WHERE " + dialect.quoteIdentifier(rel.getJoinColumn()) + " = ?";
                sqlExecutor.executeUpdate(deleteSql, new Object[]{id});
            }
        }

        String sql = dialect.generateDelete(dtoMetadata);
        int rowsAffected = sqlExecutor.executeUpdate(sql, new Object[]{id});
        if (rowsAffected == 0) {
            throw new VictorException("Entity not found for deletion: " + id);
        }
    }

    private void delete(DTO dto) {
        Object idValue = getDtoIdValue(dto);
        if (idValue == null) {
            throw new VictorException("Cannot delete entity without ID");
        }
        @SuppressWarnings("unchecked")
        ID id = (ID) idValue;
        deleteById(id);
    }

    private boolean existsById(ID id) {
        String sql = dialect.generateExists(dtoMetadata);
        return sqlExecutor.executeCount(sql, new Object[]{id}) > 0;
    }

    private long count() {
        String sql = dialect.generateCount(dtoMetadata);
        return sqlExecutor.executeCount(sql, null);
    }

    private List<DTO> saveAll(Collection<DTO> dtos) {
        List<DTO> dtoList = dtos.stream().toList();
        if (dtoList.isEmpty()) return List.of();

        boolean allNew = dtoList.stream().allMatch(this::isDtoNew);
        boolean allExisting = dtoList.stream().noneMatch(this::isDtoNew);

        if (dtoMetadata.getIdField().isAutoGenerated() && allNew) {
            return batchInsert(dtoList);
        } else if (allExisting) {
            return batchUpsert(dtoList);
        } else {
            return dtoList.stream().map(this::save).toList();
        }
    }

    private boolean isDtoNew(DTO dto) {
        Object idValue = getDtoIdValue(dto);
        return idValue == null || isDefaultId(idValue);
    }

    private List<DTO> batchInsert(List<DTO> dtos) {
        return dtos.stream().map(this::save).toList();
    }

    private List<DTO> batchUpsert(List<DTO> dtos) {
        return dtos.stream().map(this::save).toList();
    }

    private void deleteAll(Collection<DTO> dtos) {
        dtos.forEach(this::delete);
    }

    private void deleteAll() {
        String tableName = dialect.getFullTableName(dtoMetadata);
        sqlExecutor.executeUpdate("DELETE FROM " + tableName, null);
    }

    private Query<DTO> query() {
        return new QueryProxyHandler<>(dtoClass, dtoMetadata, sqlExecutor, dialect).createProxy();
    }

    private Object handleDynamicFinderMethod(Method method, Object[] args) {
        try {
            MethodNameParser.ParsedQuery parsedQuery = MethodNameParser.parse(method);
            String sql = sqlGenerator.generateSql(parsedQuery);
            Object[] preparedArgs = prepareArguments(parsedQuery, args);
            SqlExecutor.RowMapper<DTO> mapper = DtoMapper.createMapper(dtoClass, dtoMetadata, sqlExecutor);

            Class<?> returnType = method.getReturnType();
            if (returnType == Optional.class) {
                return Optional.ofNullable(sqlExecutor.executeQuerySingle(sql, preparedArgs, mapper));
            } else if (List.class.isAssignableFrom(returnType)) {
                return sqlExecutor.executeQuery(sql, preparedArgs, mapper);
            } else if (returnType.isAssignableFrom(dtoClass)) {
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

    private Object[] extractNonIdFieldValues(DTO dto) {
        return dtoMetadata.getNonIdFields().stream()
                .map(field -> getFieldValueForPersist(dto, field))
                .toArray();
    }

    private Object[] extractAllFieldValuesWithId(DTO dto) {
        return dtoMetadata.getFields().stream()
                .map(field -> getFieldValueForPersist(dto, field))
                .toArray();
    }

    /**
     * Gets the value for a field, handling synthetic FK fields by extracting
     * the related DTO's ID from the relationship field.
     */
    private Object getFieldValueForPersist(DTO dto, FieldMetadata field) {
        if (field.isSyntheticFk()) {
            return extractForeignKeyValue(dto, field.getColumnName());
        }
        return getDtoFieldValue(dto, field.getFieldName());
    }

    /**
     * For a synthetic FK column, finds the owning relationship and extracts
     * the related DTO's ID value.
     */
    private Object extractForeignKeyValue(DTO dto, String fkColumnName) {
        RelationshipMetadata rel = dtoMetadata.getOwningSideRelationships().stream()
                .filter(r -> r.getForeignKeyColumn().equals(fkColumnName))
                .findFirst().orElse(null);
        if (rel == null) return null;

        Object relatedDto = getDtoFieldValue(dto, rel.getFieldName());
        if (relatedDto == null) return null;

        DtoMetadata targetMeta = DtoMetadataRegistry.getInstance().getMetadata(rel.getTargetDtoClass());
        return targetMeta.getIdField().getValue(relatedDto);
    }

    private Object getDtoIdValue(DTO dto) {
        return getDtoFieldValue(dto, dtoMetadata.getIdField().getFieldName());
    }

    private Object getDtoFieldValue(DTO dto, String fieldName) {
        try {
            if (dtoClass.isRecord()) {
                Method accessor = dtoClass.getMethod(fieldName);
                return accessor.invoke(dto);
            } else {
                java.lang.reflect.Field dtoField = findDtoField(dtoClass, fieldName);
                if (dtoField != null) {
                    dtoField.setAccessible(true);
                    return dtoField.get(dto);
                }
                throw new VictorException("Field not found in DTO: " + fieldName);
            }
        } catch (Exception e) {
            throw new VictorException("Failed to extract field value from DTO: " + fieldName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private DTO createDtoWithId(DTO dto, ID generatedId) {
        String idFieldName = dtoMetadata.getIdField().getFieldName();
        try {
            if (dtoClass.isRecord()) {
                var components = dtoClass.getRecordComponents();
                Object[] args = new Object[components.length];
                Class<?>[] types = new Class<?>[components.length];

                for (int i = 0; i < components.length; i++) {
                    String componentName = components[i].getName();
                    types[i] = components[i].getType();
                    if (componentName.equals(idFieldName)) {
                        args[i] = generatedId;
                    } else {
                        Method accessor = dtoClass.getMethod(componentName);
                        args[i] = accessor.invoke(dto);
                    }
                }
                return (DTO) dtoClass.getDeclaredConstructor(types).newInstance(args);
            } else {
                java.lang.reflect.Field dtoField = findDtoField(dtoClass, idFieldName);
                if (dtoField != null) {
                    dtoField.setAccessible(true);
                    dtoField.set(dto, generatedId);
                }
                return dto;
            }
        } catch (Exception e) {
            throw new VictorException("Failed to create DTO with generated ID", e);
        }
    }

    private java.lang.reflect.Field findDtoField(Class<?> clazz, String fieldName) {
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
    public static <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID,
            T extends Repository<DTO, MODEL, ID>> T createProxy(
            Class<T> repositoryInterface, SqlExecutor sqlExecutor, Dialect dialect) {
        var handler = new RepositoryProxyHandler<>(repositoryInterface, sqlExecutor, dialect);
        return (T) Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class[]{repositoryInterface},
                handler
        );
    }
}