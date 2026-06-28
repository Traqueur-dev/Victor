package fr.traqueur.victor.entity.metadata;

import fr.traqueur.victor.annotations.Embedded;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.exceptions.VictorConfigurationException;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Describes an {@code @Embedded} record component of an entity. The embedded
 * record's own components are flattened into columns of the owning table; this
 * descriptor keeps the grouping needed to rebuild the nested record on read.
 */
public final class EmbeddedMetadata {

    private final String fieldName;          // component name on the owning entity (e.g. "audit")
    private final Method accessor;           // owning entity -> embedded instance
    private final Class<?> embeddedType;     // the embedded record class
    private final List<FieldMetadata> subFields; // one per embedded record component, canonical order

    private EmbeddedMetadata(String fieldName, Method accessor, Class<?> embeddedType, List<FieldMetadata> subFields) {
        this.fieldName = fieldName;
        this.accessor = accessor;
        this.embeddedType = embeddedType;
        this.subFields = Collections.unmodifiableList(subFields);
    }

    /**
     * Builds the descriptor for an {@code @Embedded} component, flattening each of
     * the embedded record's components into a {@link FieldMetadata} column.
     */
    public static EmbeddedMetadata of(RecordComponent component, String prefix) {
        Class<?> embeddedType = component.getType();
        if (!embeddedType.isRecord()) {
            throw new VictorConfigurationException(
                    "@Embedded type must be a record: " + embeddedType + " (component '" + component.getName() + "')");
        }

        Method accessor = component.getAccessor();
        accessor.setAccessible(true);

        List<FieldMetadata> subFields = new ArrayList<>();
        for (RecordComponent sub : embeddedType.getRecordComponents()) {
            if (sub.getAnnotation(Id.class) != null) {
                throw new VictorConfigurationException(
                        "@Embedded type must not declare an @Id: " + embeddedType);
            }
            if (sub.getAnnotation(Embedded.class) != null) {
                throw new VictorConfigurationException(
                        "Nested @Embedded is not supported: " + embeddedType + "." + sub.getName());
            }
            subFields.add(FieldMetadata.forEmbedded(sub, accessor, prefix));
        }

        return new EmbeddedMetadata(component.getName(), accessor, embeddedType, subFields);
    }

    public String getFieldName() { return fieldName; }
    public Method getAccessor() { return accessor; }
    public Class<?> getEmbeddedType() { return embeddedType; }
    public List<FieldMetadata> getSubFields() { return subFields; }
}
