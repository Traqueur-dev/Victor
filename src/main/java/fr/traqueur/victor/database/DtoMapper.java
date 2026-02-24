package fr.traqueur.victor.database;

import fr.traqueur.victor.annotations.FetchType;
import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.dialect.Dialect;
import fr.traqueur.victor.entities.metadata.DtoMetadata;
import fr.traqueur.victor.entities.metadata.FieldMetadata;
import fr.traqueur.victor.entities.metadata.RelationshipMetadata;
import fr.traqueur.victor.exceptions.VictorConversionException;
import fr.traqueur.victor.registries.DtoMetadataRegistry;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class DtoMapper {

    public static <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID>
    SqlExecutor.RowMapper<DTO> createMapper(Class<DTO> dtoClass, DtoMetadata metadata, SqlExecutor sqlExecutor) {
        return rs -> mapResultSetToDto(rs, dtoClass, metadata, sqlExecutor);
    }

    private static <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID> DTO mapResultSetToDto(
            ResultSet rs, Class<DTO> dtoClass, DtoMetadata metadata, SqlExecutor sqlExecutor) {
        try {
            if (dtoClass.isRecord()) {
                return mapToRecord(rs, dtoClass, metadata, sqlExecutor);
            } else {
                return mapToClass(rs, dtoClass, metadata, sqlExecutor);
            }
        } catch (Exception e) {
            throw new VictorConversionException(ResultSet.class, dtoClass,
                    "Failed to map ResultSet to DTO", e);
        }
    }

    private static <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID> DTO mapToRecord(
            ResultSet rs, Class<DTO> recordClass, DtoMetadata metadata, SqlExecutor sqlExecutor)
            throws Exception {

        RecordComponent[] components = recordClass.getRecordComponents();
        Object[] args = new Object[components.length];

        // Read the current entity's ID (needed for ONE_TO_MANY loading)
        Object currentId = readCurrentId(rs, metadata);
        Dialect dialect = sqlExecutor.dialect();

        for (int i = 0; i < components.length; i++) {
            String componentName = components[i].getName();
            Class<?> componentType = components[i].getType();

            RelationshipMetadata rel = metadata.findRelationshipByFieldName(componentName);
            if (rel != null) {
                if (rel.getFetchType() == FetchType.EAGER
                        && !RelationshipLoadingContext.isAlreadyLoading(rel.getTargetDtoClass())) {
                    args[i] = loadRelationship(rel, rs, currentId, sqlExecutor, dialect);
                } else {
                    args[i] = getDefaultValue(componentType);
                }
                continue;
            }

            FieldMetadata fieldMetadata = findFieldByName(metadata, componentName);
            if (fieldMetadata != null) {
                Object value = sqlExecutor.getFieldValue(rs, fieldMetadata);
                args[i] = convertValue(value, componentType);
            } else {
                try {
                    Object value = rs.getObject(componentName);
                    args[i] = convertValue(value, componentType);
                } catch (SQLException e) {
                    args[i] = getDefaultValue(componentType);
                }
            }
        }

        Constructor<DTO> constructor = recordClass.getDeclaredConstructor(
                Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new)
        );
        return constructor.newInstance(args);
    }

    private static <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID> DTO mapToClass(
            ResultSet rs, Class<DTO> dtoClass, DtoMetadata metadata, SqlExecutor sqlExecutor)
            throws Exception {

        DTO dto = dtoClass.getDeclaredConstructor().newInstance();
        Object currentId = readCurrentId(rs, metadata);
        Dialect dialect = sqlExecutor.dialect();

        for (var field : dtoClass.getDeclaredFields()) {
            field.setAccessible(true);
            String fieldName = field.getName();

            RelationshipMetadata rel = metadata.findRelationshipByFieldName(fieldName);
            if (rel != null) {
                if (rel.getFetchType() == FetchType.EAGER
                        && !RelationshipLoadingContext.isAlreadyLoading(rel.getTargetDtoClass())) {
                    field.set(dto, loadRelationship(rel, rs, currentId, sqlExecutor, dialect));
                }
                continue;
            }

            FieldMetadata fieldMetadata = findFieldByName(metadata, fieldName);
            if (fieldMetadata != null) {
                Object value = sqlExecutor.getFieldValue(rs, fieldMetadata);
                field.set(dto, convertValue(value, field.getType()));
            } else {
                try {
                    Object value = rs.getObject(fieldName);
                    field.set(dto, convertValue(value, field.getType()));
                } catch (SQLException e) {
                    // Column doesn't exist in result set, skip
                }
            }
        }

        return dto;
    }

    private static Object readCurrentId(ResultSet rs, DtoMetadata metadata) {
        try {
            return rs.getObject(metadata.getIdField().getColumnName());
        } catch (SQLException e) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object loadRelationship(RelationshipMetadata rel, ResultSet currentRs,
                                           Object currentId, SqlExecutor sqlExecutor, Dialect dialect) {
        RelationshipLoadingContext.push(rel.getTargetDtoClass());
        try {
            DtoMetadata targetMeta = DtoMetadataRegistry.getInstance().getMetadata(rel.getTargetDtoClass());
            SqlExecutor.RowMapper mapper = createMapper((Class) rel.getTargetDtoClass(), targetMeta, sqlExecutor);

            return switch (rel.getType()) {
                case MANY_TO_ONE -> {
                    Object fkValue = readFkFromRs(currentRs, rel.getForeignKeyColumn());
                    if (fkValue == null) yield null;
                    yield sqlExecutor.executeQuerySingle(
                            dialect.generateSelectById(targetMeta), new Object[]{fkValue}, mapper);
                }
                case ONE_TO_MANY -> {
                    String fkColumn = resolveMappedByFkColumn(targetMeta, rel.getMappedByField());
                    if (fkColumn == null || currentId == null) yield List.of();
                    String sql = "SELECT * FROM " + dialect.getFullTableName(targetMeta)
                            + " WHERE " + dialect.quoteIdentifier(fkColumn) + " = ?";
                    yield sqlExecutor.executeQuery(sql, new Object[]{currentId}, mapper);
                }
                case MANY_TO_MANY -> {
                    if (currentId == null) yield List.of();
                    String joinTable = dialect.quoteIdentifier(rel.getJoinTable());
                    String targetTable = dialect.getFullTableName(targetMeta);
                    String inverseJoinCol = dialect.quoteIdentifier(rel.getInverseJoinColumn());
                    String targetIdCol = dialect.quoteIdentifier(targetMeta.getIdField().getColumnName());
                    String joinCol = dialect.quoteIdentifier(rel.getJoinColumn());
                    String sql = "SELECT t.* FROM " + joinTable + " jt"
                            + " JOIN " + targetTable + " t ON jt." + inverseJoinCol + " = t." + targetIdCol
                            + " WHERE jt." + joinCol + " = ?";
                    yield sqlExecutor.executeQuery(sql, new Object[]{currentId}, mapper);
                }
                case ONE_TO_ONE -> {
                    if (rel.ownsForeignKey()) {
                        Object fkValue = readFkFromRs(currentRs, rel.getForeignKeyColumn());
                        if (fkValue == null) yield null;
                        yield sqlExecutor.executeQuerySingle(
                                dialect.generateSelectById(targetMeta), new Object[]{fkValue}, mapper);
                    } else {
                        String fkColumn = resolveMappedByFkColumn(targetMeta, rel.getMappedByField());
                        if (fkColumn == null || currentId == null) yield null;
                        String sql = "SELECT * FROM " + dialect.getFullTableName(targetMeta)
                                + " WHERE " + dialect.quoteIdentifier(fkColumn) + " = ?";
                        yield sqlExecutor.executeQuerySingle(sql, new Object[]{currentId}, mapper);
                    }
                }
            };
        } finally {
            RelationshipLoadingContext.pop(rel.getTargetDtoClass());
        }
    }

    private static Object readFkFromRs(ResultSet rs, String fkColumn) {
        try {
            return rs.getObject(fkColumn);
        } catch (SQLException e) {
            return null;
        }
    }

    private static String resolveMappedByFkColumn(DtoMetadata targetMeta, String mappedByField) {
        RelationshipMetadata inverseRel = targetMeta.getRelationships().stream()
                .filter(r -> r.getFieldName().equals(mappedByField))
                .findFirst().orElse(null);
        return (inverseRel != null) ? inverseRel.getForeignKeyColumn() : null;
    }

    private static FieldMetadata findFieldByName(DtoMetadata metadata, String name) {
        FieldMetadata field = metadata.getFieldByColumn(name);
        if (field != null) return field;
        return metadata.getFieldByName(name);
    }

    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return getDefaultValue(targetType);
        if (targetType.isInstance(value)) return value;
        if (targetType == String.class) return value.toString();
        if (targetType == UUID.class && value instanceof String str) return UUID.fromString(str);

        if (value instanceof Number number) {
            if (targetType == Long.class || targetType == long.class) return number.longValue();
            if (targetType == Integer.class || targetType == int.class) return number.intValue();
            if (targetType == Double.class || targetType == double.class) return number.doubleValue();
            if (targetType == Float.class || targetType == float.class) return number.floatValue();
            if (targetType == Short.class || targetType == short.class) return number.shortValue();
            if (targetType == Byte.class || targetType == byte.class) return number.byteValue();
        }

        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Number number) return number.intValue() != 0;
            if (value instanceof String str) return Boolean.parseBoolean(str);
        }

        if (targetType == LocalDateTime.class) {
            if (value instanceof Timestamp ts) return ts.toLocalDateTime();
            if (value instanceof String str) return LocalDateTime.parse(str);
        }

        return value;
    }

    private static Object getDefaultValue(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == boolean.class) return false;
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0.0f;
            if (type == double.class) return 0.0d;
            if (type == char.class) return '\0';
        }
        return null;
    }
}