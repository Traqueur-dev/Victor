package fr.traqueur.victor.entity.metadata;

import fr.traqueur.victor.annotations.*;
import fr.traqueur.victor.exceptions.VictorConfigurationException;
import fr.traqueur.victor.registries.EntityMetadataRegistry;
import fr.traqueur.victor.utils.StringUtils;

import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.stream.Collectors;

/**
 * E-centric metadata: reads @Table, @Column, @Id, and relationship annotations
 * directly from the E class (record or regular class).
 *
 * <p>This is the primary source of truth for schema information in Victor.
 * Entities are agnostic; DTOs carry all persistence metadata.</p>
 */
public final class EntityMetadata {

    private final Class<?> entityClass;
    private final String tableName;
    private final String schema;
    private final FieldMetadata idField;
    private final List<FieldMetadata> scalarFields;         // @Id + @Column fields (no relations)
    private final List<RelationshipMetadata> relationships;
    private final Map<String, FieldMetadata> fieldByColumn; // keyed by column name
    private final Map<String, FieldMetadata> fieldByName;   // keyed by Java field/component name

    private EntityMetadata(Class<?> entityClass) {
        this.entityClass = entityClass;

        if (!entityClass.isRecord()) {
            throw new VictorConfigurationException("Victor entities must be Java records: " + entityClass);
        }

        var tableAnn = entityClass.getAnnotation(Table.class);
        if (tableAnn == null) {
            throw new VictorConfigurationException("E must be annotated with @Table: " + entityClass);
        }

        this.tableName = tableAnn.table().isEmpty()
            ? StringUtils.camelToSnakeCase(entityClass.getSimpleName())
            : tableAnn.table();
        this.schema = tableAnn.schema().isEmpty() ? null : tableAnn.schema();

        List<FieldMetadata> scalars = new ArrayList<>();
        List<RelationshipMetadata> rels = new ArrayList<>();

        analyzeRecord(entityClass, scalars, rels);

        FieldMetadata id = scalars.stream().filter(FieldMetadata::isId).findFirst().orElse(null);
        if (id == null) {
            throw new VictorConfigurationException("No @Id field found in E: " + entityClass);
        }

        this.idField = id;
        this.scalarFields = Collections.unmodifiableList(scalars);
        this.relationships = Collections.unmodifiableList(rels);

        this.fieldByColumn = scalars.stream()
            .collect(Collectors.toMap(FieldMetadata::getColumnName, f -> f));
        this.fieldByName = scalars.stream()
            .collect(Collectors.toMap(FieldMetadata::getFieldName, f -> f));
    }

    public static EntityMetadata of(Class<?> entityClass) {
        return new EntityMetadata(entityClass);
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
                    throw new VictorConfigurationException("Multiple @Id fields found in E: " + clazz);
                }
            }
        }
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
     * FK column types are resolved lazily via the target E's id field.
     */
    public List<FieldMetadata> getAllPersistableNonIdFields() {
        List<FieldMetadata> result = new ArrayList<>(getNonIdScalarFields());
        for (RelationshipMetadata rel : relationships) {
            if (rel.ownsForeignKey()) {
                Class<?> idType = resolveTargetIdType(rel.getTargetEntityClass());
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
     * EntityMetadata.getAllPersistableFields() so dialect SQL generators remain unchanged.
     */
    public List<FieldMetadata> getFields() {
        return getAllPersistableFields();
    }

    /**
     * Alias for {@link #getAllPersistableNonIdFields()} — exposes the same API as the former
     * EntityMetadata.getAllPersistableNonIdFields() so dialect SQL generators remain unchanged.
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

    private Class<?> resolveTargetIdType(Class<?> targetEntityClass) {
        try {
            return EntityMetadataRegistry.getInstance().getMetadata(targetEntityClass).getIdField().getJavaType();
        } catch (Exception e) {
            return Long.class; // safe fallback
        }
    }

    public Class<?> getEntityClass() { return entityClass; }
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