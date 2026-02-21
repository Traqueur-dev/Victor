package fr.traqueur.victor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface ManyToMany {

    /** Name of the junction table (e.g. "user_roles"). */
    String joinTable();

    /** FK column in the junction table pointing to the current entity (e.g. "user_id"). */
    String joinColumn();

    /** FK column in the junction table pointing to the target entity (e.g. "role_id"). */
    String inverseJoinColumn();

    FetchType fetch() default FetchType.EAGER;

    CascadeType[] cascade() default {};
}