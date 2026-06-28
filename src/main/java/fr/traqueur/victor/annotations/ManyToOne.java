package fr.traqueur.victor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface ManyToOne {

    Class<?> targetEntity();

    /**
     * Name of the FK column in the current table.
     * Defaults to fieldName + "_id" if empty.
     */
    String column() default "";

    boolean nullable() default true;

    FetchType fetch() default FetchType.EAGER;

    CascadeType[] cascade() default {};
}