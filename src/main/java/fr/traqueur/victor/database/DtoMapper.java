package fr.traqueur.victor.database;

import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.entities.metadata.FieldMetadata;
import fr.traqueur.victor.exceptions.VictorConversionException;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

public final class DtoMapper {

    public static <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID>
    SqlExecutor.RowMapper<DTO> createMapper(Class<DTO> dtoClass, EntityMetadata metadata, SqlExecutor sqlExecutor) {

        return rs -> mapResultSetToDto(rs, dtoClass, metadata, sqlExecutor);
    }

    private static <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID> DTO mapResultSetToDto(
            ResultSet rs, Class<DTO> dtoClass, EntityMetadata metadata, SqlExecutor sqlExecutor) {

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
            ResultSet rs, Class<DTO> recordClass, EntityMetadata metadata, SqlExecutor sqlExecutor)
            throws Exception {

        RecordComponent[] components = recordClass.getRecordComponents();
        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            String componentName = components[i].getName();
            Class<?> componentType = components[i].getType();

            // Find corresponding field metadata
            FieldMetadata fieldMetadata = findFieldByName(metadata, componentName);

            if (fieldMetadata != null) {
                // Use SqlExecutor to get typed value
                Object value = sqlExecutor.getFieldValue(rs, fieldMetadata);
                args[i] = convertValue(value, componentType);
            } else {
                // Try to get value by column name directly
                try {
                    Object value = rs.getObject(componentName);
                    args[i] = convertValue(value, componentType);
                } catch (SQLException e) {
                    // Column doesn't exist, set to null/default
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
            ResultSet rs, Class<DTO> dtoClass, EntityMetadata metadata, SqlExecutor sqlExecutor)
            throws Exception {

        DTO dto = dtoClass.getDeclaredConstructor().newInstance();

        var fields = dtoClass.getDeclaredFields();
        for (var field : fields) {
            field.setAccessible(true);
            String fieldName = field.getName();

            // Find corresponding field metadata
            FieldMetadata fieldMetadata = findFieldByName(metadata, fieldName);

            if (fieldMetadata != null) {
                Object value = sqlExecutor.getFieldValue(rs, fieldMetadata);
                Object convertedValue = convertValue(value, field.getType());
                field.set(dto, convertedValue);
            } else {
                // Try to get value by column name directly
                try {
                    Object value = rs.getObject(fieldName);
                    Object convertedValue = convertValue(value, field.getType());
                    field.set(dto, convertedValue);
                } catch (SQLException e) {
                    // Column doesn't exist, skip this field
                }
            }
        }

        return dto;
    }

    private static FieldMetadata findFieldByName(EntityMetadata metadata, String name) {
        // First try exact column name match
        FieldMetadata field = metadata.getField(name);
        if (field != null) {
            return field;
        }

        // Then try to find by field name
        return metadata.getFields().stream()
                .filter(f -> f.getField().getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return getDefaultValue(targetType);
        }

        if (targetType.isInstance(value)) {
            return value;
        }

        // String conversions
        if (targetType == String.class) {
            return value.toString();
        }

        if(targetType == UUID.class) {
            if(value instanceof String str) {
                return UUID.fromString(str);
            }
        }

        // Number conversions
        if (value instanceof Number number) {
            if (targetType == Long.class || targetType == long.class) {
                return number.longValue();
            } else if (targetType == Integer.class || targetType == int.class) {
                return number.intValue();
            } else if (targetType == Double.class || targetType == double.class) {
                return number.doubleValue();
            } else if (targetType == Float.class || targetType == float.class) {
                return number.floatValue();
            } else if (targetType == Short.class || targetType == short.class) {
                return number.shortValue();
            } else if (targetType == Byte.class || targetType == byte.class) {
                return number.byteValue();
            }
        }

        // Boolean conversions
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Number number) {
                return number.intValue() != 0; // SQLite stores booleans as integers
            } else if (value instanceof String str) {
                return Boolean.parseBoolean(str);
            }
        }

        // LocalDateTime conversions
        if (targetType == LocalDateTime.class) {
            if (value instanceof Timestamp ts) {
                return ts.toLocalDateTime();
            } else if (value instanceof String str) {
                return LocalDateTime.parse(str);
            }
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