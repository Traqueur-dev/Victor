package fr.traqueur.victor.entities.metadata;

import fr.traqueur.victor.annotations.*;
import fr.traqueur.victor.exceptions.VictorConfigurationException;
import fr.traqueur.victor.registries.DtoMetadataRegistry;
import fr.traqueur.victor.utils.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DTO-centric metadata: reads @Table, @Column, @Id, and relationship annotations
 * directly from the DTO class (record or regular class).
 *
 * <p>This is the primary source of truth for schema information in Victor.
 * Entities are agnostic; DTOs carry all persistence metadata.</p>
 */
public final class DtoMetadata {

    private final Class<?> dtoClass;
    private final String tableName;
    private final String schema;
    private final FieldMetadata idField;
    private final List<FieldMetadata> scalarFields;         // @Id + @Column fields (no relations)
    private final List<RelationshipMetadata> relationships;
    private final Map<String, FieldMetadata> fieldByColumn; // keyed by column name
    private final Map<String, FieldMetadata> fieldByName;   // keyed by Java field/component name

    private DtoMetadata(Class<?> dtoClass) {
        this.dtoClass = dtoClass;

        var tableAnn = dtoClass.getAnnotation(Table.class);
        if (tableAnn == null) {
            throw new VictorConfigurationException("DTO must be annotated with @Table: " + dtoClass);
        }

        this.tableName = tableAnn.table().isEmpty()
            ? StringUtils.camelToSnakeCase(dtoClass.getSimpleName())
            : tableAnn.table();
        this.schema = tableAnn.schema().isEmpty() ? null : tableAnn.schema();

        List<FieldMetadata> scalars = new ArrayList<>();
        List<RelationshipMetadata> rels = new ArrayList<>();

        if (dtoClass.isRecord()) {
            analyzeRecord(dtoClass, scalars, rels);
        } else {
            analyzeClass(dtoClass, scalars, rels);
        }

        FieldMetadata id = scalars.stream().filter(FieldMetadata::isId).findFirst().orElse(null);
        if (id == null) {
            throw new VictorConfigurationException("No @Id field found in DTO: " + dtoClass);
        }

        this.idField = id;
        this.scalarFields = Collections.unmodifiableList(scalars);
        this.relationships = Collections.unmodifiableList(rels);

        this.fieldByColumn = scalars.stream()
            .collect(Collectors.toMap(FieldMetadata::getColumnName, f -> f));
        this.fieldByName = scalars.stream()
            .collect(Collectors.toMap(FieldMetadata::getFieldName, f -> f));
    }

    public static DtoMetadata of(Class<?> dtoClass) {
        return new DtoMetadata(dtoClass);
    }

    // -------------------------------------------------------------------------
    // Record analysis
    // -------------------------------------------------------------------------

    private void analyzeRecord(Class<?> clazz, List<FieldMetadata> scalars, List<RelationshipMetadata> rels) {
        int idCount = 0;
        for (RecordComponent component : clazz.getRecordComponents()) {
            if (component.getAnnotation(Ignore.class) != null) continue;

            // Check relationship annotations first
            RelationshipMetadata rel = RelationshipMetadata.ofRecordComponent(component);
            if (rel != null) {
                rels.add(rel);
                continue;
            }

            // Scalar field
            FieldMetadata fm = FieldMetadata.ofRecordComponent(component);
            scalars.add(fm);

            if (fm.isId()) {
                idCount++;
                if (idCount > 1) {
                    throw new VictorConfigurationException("Multiple @Id fields found in DTO: " + clazz);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Class analysis (class-based DTOs)
    // -------------------------------------------------------------------------

    private void analyzeClass(Class<?> clazz, List<FieldMetadata> scalars, List<RelationshipMetadata> rels) {
        int idCount = 0;
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (shouldIgnoreField(field)) continue;

                RelationshipMetadata rel = RelationshipMetadata.ofField(field);
                if (rel != null) {
                    rels.add(rel);
                    continue;
                }

                FieldMetadata fm = FieldMetadata.of(field);
                scalars.add(fm);

                if (fm.isId()) {
                    idCount++;
                    if (idCount > 1) {
                        throw new VictorConfigurationException("Multiple @Id fields found in DTO: " + clazz);
                    }
                }
            }
            current = current.getSuperclass();
        }
    }

    private boolean shouldIgnoreField(Field field) {
        return field.isAnnotationPresent(Ignore.class)
            || Modifier.isStatic(field.getModifiers())
            || Modifier.isTransient(field.getModifiers());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** All scalar fields including the @Id field. */
    public List<FieldMetadata> getScalarFields() { return scalarFields; }

    /** Scalar non-@Id fields. */
    public List<FieldMetadata> getNonIdScalarFields() {
        return scalarFields.stream().filter(f -> !f.isId()).collect(Collectors.toList());
    }

    /**
     * All persistable non-ID fields: scalar non-ID columns + synthetic FK columns.
     * This is the canonical list for INSERT column lists and UPDATE SET clauses.
     * FK column types are resolved lazily via the target DTO's id field.
     */
    public List<FieldMetadata> getAllPersistableNonIdFields() {
        List<FieldMetadata> result = new ArrayList<>(getNonIdScalarFields());
        for (RelationshipMetadata rel : relationships) {
            if (rel.ownsForeignKey()) {
                Class<?> idType = resolveTargetIdType(rel.getTargetDtoClass());
                result.add(FieldMetadata.forForeignKey(rel.getForeignKeyColumn(), idType, rel.isNullable()));
            }
        }
        return result;
    }

    /**
     * All persistable fields including the ID: id + scalar non-ID + synthetic FK columns.
     * Used for UPSERT column lists.
     */
    public List<FieldMetadata> getAllPersistableFields() {
        List<FieldMetadata> result = new ArrayList<>();
        result.add(idField);
        result.addAll(getAllPersistableNonIdFields());
        return result;
    }

    /**
     * Alias for {@link #getAllPersistableFields()} — exposes the same API as the former
     * DtoMetadata.getAllPersistableFields() so dialect SQL generators remain unchanged.
     */
    public List<FieldMetadata> getFields() {
        return getAllPersistableFields();
    }

    /**
     * Alias for {@link #getAllPersistableNonIdFields()} — exposes the same API as the former
     * DtoMetadata.getAllPersistableNonIdFields() so dialect SQL generators remain unchanged.
     */
    public List<FieldMetadata> getNonIdFields() {
        return getAllPersistableNonIdFields();
    }

    /** Owning-side relationships (those that have a FK column in this table). */
    public List<RelationshipMetadata> getOwningSideRelationships() {
        return relationships.stream()
            .filter(RelationshipMetadata::ownsForeignKey)
            .collect(Collectors.toList());
    }

    private Class<?> resolveTargetIdType(Class<?> targetDtoClass) {
        try {
            return DtoMetadataRegistry.getInstance().getMetadata(targetDtoClass).getIdField().getJavaType();
        } catch (Exception e) {
            return Long.class; // safe fallback
        }
    }

    public Class<?> getDtoClass() { return dtoClass; }
    public String getTableName() { return tableName; }
    public String getSchema() { return schema; }
    public FieldMetadata getIdField() { return idField; }
    public List<RelationshipMetadata> getRelationships() { return relationships; }

    public String getFullTableName() {
        return schema != null ? schema + "." + tableName : tableName;
    }

    public FieldMetadata getFieldByColumn(String columnName) {
        return fieldByColumn.get(columnName);
    }

    public FieldMetadata getFieldByName(String fieldName) {
        return fieldByName.get(fieldName);
    }

    public RelationshipMetadata findRelationshipByFieldName(String fieldName) {
        return relationships.stream()
            .filter(r -> r.getFieldName().equals(fieldName))
            .findFirst()
            .orElse(null);
    }
}