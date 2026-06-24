package fr.traqueur.victor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface OneToMany {

    /**
     * Name of the @ManyToOne field in the target E that holds the FK back to this entity.
     */
    String mappedBy();

    FetchType fetch() default FetchType.EAGER;

    CascadeType[] cascade() default {};
}