package fr.traqueur.victor.conversion;

import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.exceptions.VictorConversionException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;

public final class VictorConverter {
    
    @SuppressWarnings("unchecked")
    public static <DTO extends Dto<MODEL>, MODEL extends Entity<?>>
           DTO modelToDto(MODEL model, Class<DTO> dtoClass) {
        
        if (model == null) return null;
        
        try {
            if (dtoClass.isRecord()) {
                return convertModelToRecord(model, dtoClass);
            } else {
                return convertModelToClass(model, dtoClass);
            }
        } catch (Exception e) {
            throw new VictorConversionException(model.getClass(), dtoClass, "Failed to convert model to DTO", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    public static <MODEL extends Entity<?>> MODEL dtoToModel(Dto<MODEL> dto, Class<MODEL> modelClass) {
        if (dto == null) return null;
        
        try {
            MODEL model = createModelInstance(modelClass);
            copyFieldsFromDto(dto, model);
            return model;
        } catch (Exception e) {
            throw new VictorConversionException(dto.getClass(), modelClass, "Failed to convert DTO to model", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private static <DTO> DTO convertModelToRecord(Object model, Class<DTO> recordClass) throws Exception {
        RecordComponent[] components = recordClass.getRecordComponents();
        Object[] args = new Object[components.length];
        
        for (int i = 0; i < components.length; i++) {
            String fieldName = components[i].getName();
            Class<?> targetType = components[i].getType();
            
            Field modelField = findField(model.getClass(), fieldName);
            if (modelField != null) {
                modelField.setAccessible(true);
                Object value = modelField.get(model);
                args[i] = convertValue(value, targetType);
            }
        }
        
        Constructor<DTO> constructor = recordClass.getDeclaredConstructor(
            Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new)
        );
        
        return constructor.newInstance(args);
    }
    
    @SuppressWarnings("unchecked")
    private static <DTO> DTO convertModelToClass(Object model, Class<DTO> dtoClass) throws Exception {
        DTO dto = dtoClass.getDeclaredConstructor().newInstance();
        
        Field[] dtoFields = dtoClass.getDeclaredFields();
        for (Field dtoField : dtoFields) {
            dtoField.setAccessible(true);
            
            Field modelField = findField(model.getClass(), dtoField.getName());
            if (modelField != null) {
                modelField.setAccessible(true);
                Object value = modelField.get(model);
                Object convertedValue = convertValue(value, dtoField.getType());
                dtoField.set(dto, convertedValue);
            }
        }
        
        return dto;
    }
    
    private static <MODEL extends Entity<?>> MODEL createModelInstance(Class<MODEL> modelClass) throws Exception {
        return modelClass.getDeclaredConstructor().newInstance();
    }
    
    private static void copyFieldsFromDto(Object dto, Object model) throws Exception {
        if (dto.getClass().isRecord()) {
            copyFromRecord(dto, model);
        } else {
            copyFromClass(dto, model);
        }
    }
    
    private static void copyFromRecord(Object dto, Object model) throws Exception {
        RecordComponent[] components = dto.getClass().getRecordComponents();
        for (RecordComponent component : components) {
            String fieldName = component.getName();
            Object value = component.getAccessor().invoke(dto);
            
            Field modelField = findField(model.getClass(), fieldName);
            if (modelField != null) {
                modelField.setAccessible(true);
                Object convertedValue = convertValue(value, modelField.getType());
                modelField.set(model, convertedValue);
            }
        }
    }
    
    private static void copyFromClass(Object dto, Object model) throws Exception {
        Field[] dtoFields = dto.getClass().getDeclaredFields();
        for (Field dtoField : dtoFields) {
            dtoField.setAccessible(true);
            Object value = dtoField.get(dto);
            
            Field modelField = findField(model.getClass(), dtoField.getName());
            if (modelField != null) {
                modelField.setAccessible(true);
                Object convertedValue = convertValue(value, modelField.getType());
                modelField.set(model, convertedValue);
            }
        }
    }
    
    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isAssignableFrom(value.getClass())) return value;
        
        // String conversions
        if (targetType == String.class) {
            if (value instanceof LocalDateTime) {
                return value.toString();
            }
            return value.toString();
        }
        
        // LocalDateTime conversions
        return switch (value) {
            case String s when targetType == LocalDateTime.class -> LocalDateTime.parse(s);

            // Number conversions
            case Number number when targetType == Long.class -> number.longValue();
            case Number number when targetType == Integer.class -> number.intValue();
            case Number number when targetType == Double.class -> number.doubleValue();
            case Number number when targetType == Float.class -> number.floatValue();
            case Number number when targetType == Short.class -> number.shortValue();
            case Number number when targetType == Byte.class -> number.byteValue();
            case Number number when  targetType == long.class -> number.longValue();
            case Number number when  targetType == int.class -> number.intValue();
            case Number number when  targetType == double.class -> number.doubleValue();
            case Number number when  targetType == float.class -> number.floatValue();
            case Number number when  targetType == short.class -> number.shortValue();
            case Number number when  targetType == byte.class -> number.byteValue();

            // Boolean conversions
            case String s when targetType == Boolean.class -> Boolean.parseBoolean(s);
            default -> value;
        };

    }
    
    private static Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            return null;
        }
    }
}