package fr.traqueur.victor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record component whose own record components are flattened into
 * columns of the owning entity's table (no separate table, no foreign key).
 *
 * <p>The embedded type must itself be a Java record and must not declare an
 * {@code @Id} or relationships. Use {@link #prefix()} to disambiguate column
 * names when embedding the same type more than once.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Embedded {

    /** Optional column-name prefix applied to every flattened column. */
    String prefix() default "";
}
