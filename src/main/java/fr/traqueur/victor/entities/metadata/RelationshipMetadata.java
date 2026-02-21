package fr.traqueur.victor.entities.metadata;

import fr.traqueur.victor.annotations.*;
import fr.traqueur.victor.utils.StringUtils;

import java.lang.reflect.RecordComponent;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Describes a relationship field on a DTO (OneToMany, ManyToOne, ManyToMany, OneToOne).
 */
public final class RelationshipMetadata {

    public enum RelationType {
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY,
        ONE_TO_ONE
    }

    private final String fieldName;
    private final Class<?> targetDtoClass;
    private final RelationType type;
    private final FetchType fetchType;
    private final CascadeType[] cascadeTypes;

    // For MANY_TO_ONE and ONE_TO_ONE (owning side): FK column in the current table
    private final String foreignKeyColumn;

    // For ONE_TO_MANY and ONE_TO_ONE (inverse side): field name in target DTO
    private final String mappedByField;

    // For MANY_TO_MANY
    private final String joinTable;
    private final String joinColumn;
    private final String inverseJoinColumn;

    // Whether the collection element is nullable (for MANY_TO_ONE / ONE_TO_ONE owning)
    private final boolean nullable;

    private RelationshipMetadata(Builder b) {
        this.fieldName = b.fieldName;
        this.targetDtoClass = b.targetDtoClass;
        this.type = b.type;
        this.fetchType = b.fetchType;
        this.cascadeTypes = b.cascadeTypes;
        this.foreignKeyColumn = b.foreignKeyColumn;
        this.mappedByField = b.mappedByField;
        this.joinTable = b.joinTable;
        this.joinColumn = b.joinColumn;
        this.inverseJoinColumn = b.inverseJoinColumn;
        this.nullable = b.nullable;
    }

    // -------------------------------------------------------------------------
    // Factory methods — from RecordComponent
    // -------------------------------------------------------------------------

    public static RelationshipMetadata ofRecordComponent(RecordComponent component) {
        String name = component.getName();

        ManyToOne mto = component.getAnnotation(ManyToOne.class);
        if (mto != null) {
            String col = mto.column().isEmpty() ? StringUtils.camelToSnakeCase(name) + "_id" : mto.column();
            return new Builder(name, mto.targetDto(), RelationType.MANY_TO_ONE)
                .foreignKeyColumn(col)
                .nullable(mto.nullable())
                .fetch(mto.fetch())
                .cascade(mto.cascade())
                .build();
        }

        OneToMany otm = component.getAnnotation(OneToMany.class);
        if (otm != null) {
            Class<?> target = resolveCollectionGeneric(component.getGenericType());
            return new Builder(name, target, RelationType.ONE_TO_MANY)
                .mappedBy(otm.mappedBy())
                .fetch(otm.fetch())
                .cascade(otm.cascade())
                .build();
        }

        ManyToMany mtm = component.getAnnotation(ManyToMany.class);
        if (mtm != null) {
            Class<?> target = resolveCollectionGeneric(component.getGenericType());
            return new Builder(name, target, RelationType.MANY_TO_MANY)
                .joinTable(mtm.joinTable())
                .joinColumn(mtm.joinColumn())
                .inverseJoinColumn(mtm.inverseJoinColumn())
                .fetch(mtm.fetch())
                .cascade(mtm.cascade())
                .build();
        }

        OneToOne oto = component.getAnnotation(OneToOne.class);
        if (oto != null) {
            boolean isOwning = oto.mappedBy().isEmpty();
            String col = isOwning
                ? (oto.column().isEmpty() ? StringUtils.camelToSnakeCase(name) + "_id" : oto.column())
                : null;
            Class<?> target = isOwning ? oto.targetDto() : resolveCollectionGeneric(component.getGenericType());
            return new Builder(name, target, RelationType.ONE_TO_ONE)
                .foreignKeyColumn(col)
                .mappedBy(oto.mappedBy())
                .nullable(oto.nullable())
                .fetch(oto.fetch())
                .cascade(oto.cascade())
                .build();
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Factory methods — from Field (class-based DTO)
    // -------------------------------------------------------------------------

    public static RelationshipMetadata ofField(Field field) {
        String name = field.getName();

        ManyToOne mto = field.getAnnotation(ManyToOne.class);
        if (mto != null) {
            String col = mto.column().isEmpty() ? StringUtils.camelToSnakeCase(name) + "_id" : mto.column();
            return new Builder(name, mto.targetDto(), RelationType.MANY_TO_ONE)
                .foreignKeyColumn(col)
                .nullable(mto.nullable())
                .fetch(mto.fetch())
                .cascade(mto.cascade())
                .build();
        }

        OneToMany otm = field.getAnnotation(OneToMany.class);
        if (otm != null) {
            Class<?> target = resolveCollectionGeneric(field.getGenericType());
            return new Builder(name, target, RelationType.ONE_TO_MANY)
                .mappedBy(otm.mappedBy())
                .fetch(otm.fetch())
                .cascade(otm.cascade())
                .build();
        }

        ManyToMany mtm = field.getAnnotation(ManyToMany.class);
        if (mtm != null) {
            Class<?> target = resolveCollectionGeneric(field.getGenericType());
            return new Builder(name, target, RelationType.MANY_TO_MANY)
                .joinTable(mtm.joinTable())
                .joinColumn(mtm.joinColumn())
                .inverseJoinColumn(mtm.inverseJoinColumn())
                .fetch(mtm.fetch())
                .cascade(mtm.cascade())
                .build();
        }

        OneToOne oto = field.getAnnotation(OneToOne.class);
        if (oto != null) {
            boolean isOwning = oto.mappedBy().isEmpty();
            String col = isOwning
                ? (oto.column().isEmpty() ? StringUtils.camelToSnakeCase(name) + "_id" : oto.column())
                : null;
            Class<?> target = isOwning ? oto.targetDto() : resolveCollectionGeneric(field.getGenericType());
            return new Builder(name, target, RelationType.ONE_TO_ONE)
                .foreignKeyColumn(col)
                .mappedBy(oto.mappedBy())
                .nullable(oto.nullable())
                .fetch(oto.fetch())
                .cascade(oto.cascade())
                .build();
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Class<?> resolveCollectionGeneric(Type genericType) {
        if (genericType instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0 && args[0] instanceof Class<?> c) {
                return c;
            }
        }
        throw new IllegalArgumentException("Cannot resolve collection element type from: " + genericType);
    }

    // -------------------------------------------------------------------------
    // Semantic helpers
    // -------------------------------------------------------------------------

    /** True if this relationship owns a FK column in the current table (MANY_TO_ONE or ONE_TO_ONE owning). */
    public boolean ownsForeignKey() {
        return type == RelationType.MANY_TO_ONE
            || (type == RelationType.ONE_TO_ONE && foreignKeyColumn != null);
    }

    /** True if this relationship holds a collection (ONE_TO_MANY or MANY_TO_MANY). */
    public boolean isCollection() {
        return type == RelationType.ONE_TO_MANY || type == RelationType.MANY_TO_MANY;
    }

    public boolean hasCascade(CascadeType cascadeType) {
        return Arrays.stream(cascadeTypes).anyMatch(c -> c == cascadeType || c == CascadeType.ALL);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getFieldName() { return fieldName; }
    public Class<?> getTargetDtoClass() { return targetDtoClass; }
    public RelationType getType() { return type; }
    public FetchType getFetchType() { return fetchType; }
    public CascadeType[] getCascadeTypes() { return cascadeTypes; }
    public String getForeignKeyColumn() { return foreignKeyColumn; }
    public String getMappedByField() { return mappedByField; }
    public String getJoinTable() { return joinTable; }
    public String getJoinColumn() { return joinColumn; }
    public String getInverseJoinColumn() { return inverseJoinColumn; }
    public boolean isNullable() { return nullable; }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    private static final class Builder {
        final String fieldName;
        final Class<?> targetDtoClass;
        final RelationType type;
        FetchType fetchType = FetchType.EAGER;
        CascadeType[] cascadeTypes = new CascadeType[0];
        String foreignKeyColumn;
        String mappedByField = "";
        String joinTable;
        String joinColumn;
        String inverseJoinColumn;
        boolean nullable = true;

        Builder(String fieldName, Class<?> targetDtoClass, RelationType type) {
            this.fieldName = fieldName;
            this.targetDtoClass = targetDtoClass;
            this.type = type;
        }

        Builder foreignKeyColumn(String v) { this.foreignKeyColumn = v; return this; }
        Builder mappedBy(String v) { this.mappedByField = v; return this; }
        Builder joinTable(String v) { this.joinTable = v; return this; }
        Builder joinColumn(String v) { this.joinColumn = v; return this; }
        Builder inverseJoinColumn(String v) { this.inverseJoinColumn = v; return this; }
        Builder nullable(boolean v) { this.nullable = v; return this; }
        Builder fetch(FetchType v) { this.fetchType = v; return this; }
        Builder cascade(CascadeType[] v) { this.cascadeTypes = v; return this; }

        RelationshipMetadata build() { return new RelationshipMetadata(this); }
    }
}