package fr.traqueur.victor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface OneToOne {

    /**
     * Target E class. Required on the owning side (the side that holds the FK column).
     * Leave default on the inverse side and use {@link #mappedBy()} instead.
     */
    Class<?> targetEntity() default Void.class;

    /**
     * FK column name in the current table (owning side).
     * Defaults to fieldName + "_id" if empty.
     */
    String column() default "";

    /**
     * Field name in the target E that holds the @OneToOne pointing back (inverse side).
     * Set this on the inverse side instead of targetEntity/column.
     */
    String mappedBy() default "";

    boolean nullable() default true;

    FetchType fetch() default FetchType.EAGER;

    CascadeType[] cascade() default {};
}