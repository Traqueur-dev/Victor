package fr.traqueur.victor.entities.metadata;

import fr.traqueur.victor.annotations.*;
import fr.traqueur.victor.exceptions.VictorConfigurationException;
import fr.traqueur.victor.utils.StringUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public final class EntityMetadata {

    private final String tableName;
    private final String schema;
    private final FieldMetadata idField;
    private final List<FieldMetadata> fields;
    private final Map<String, FieldMetadata> fieldMap;
    
    private EntityMetadata(Class<?> entityClass) {
        var entityAnnotation = entityClass.getAnnotation(Table.class);
        if (entityAnnotation == null) {
            throw new VictorConfigurationException("Class must be annotated with @Table: " + entityClass);
        }
        
        this.tableName = entityAnnotation.table().isEmpty() ? 
            StringUtils.camelToSnakeCase(entityClass.getSimpleName()) : entityAnnotation.table();
        this.schema = entityAnnotation.schema().isEmpty() ? null : entityAnnotation.schema();
        
        var fieldAnalysis = analyzeFields(entityClass);
        this.idField = fieldAnalysis.idField();
        this.fields = fieldAnalysis.fields();
        this.fieldMap = fields.stream()
            .collect(Collectors.toMap(FieldMetadata::getColumnName, f -> f));
    }
    
    public static EntityMetadata of(Class<?> entityClass) {
        return new EntityMetadata(entityClass);
    }
    
    private record FieldAnalysis(FieldMetadata idField, List<FieldMetadata> fields) {}
    
    private FieldAnalysis analyzeFields(Class<?> entityClass) {
        FieldMetadata idField = null;
        List<FieldMetadata> allFields = new ArrayList<>();
        
        for (Field field : getAllFields(entityClass)) {
            if (shouldIgnoreField(field)) {
                continue;
            }
            
            FieldMetadata fieldMetadata = FieldMetadata.of(field);
            allFields.add(fieldMetadata);
            
            if (field.isAnnotationPresent(Id.class)) {
                if (idField != null) {
                    throw new VictorConfigurationException("Multiple @VictorId fields found in " + entityClass);
                }
                idField = fieldMetadata;
            }
        }
        
        if (idField == null) {
            throw new VictorConfigurationException("No @VictorId field found in " + entityClass);
        }
        
        return new FieldAnalysis(idField, allFields);
    }
    
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        
        return fields;
    }
    
    private boolean shouldIgnoreField(Field field) {
        return field.isAnnotationPresent(Ignore.class) ||
               java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
               java.lang.reflect.Modifier.isTransient(field.getModifiers());
    }
    
    public String getTableName() { return tableName; }
    public String getSchema() { return schema; }
    public FieldMetadata getIdField() { return idField; }
    public List<FieldMetadata> getFields() { return fields; }
    public FieldMetadata getField(String columnName) { return fieldMap.get(columnName); }
    
    public String getFullTableName() {
        return schema != null ? schema + "." + tableName : tableName;
    }
    
    public List<FieldMetadata> getNonIdFields() {
        return fields.stream()
            .filter(f -> !f.isId())
            .collect(Collectors.toList());
    }
}