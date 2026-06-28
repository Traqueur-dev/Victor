package fr.traqueur.victor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Repeatable(VictorIndexes.class)
public @interface VictorIndex {

    String name() default "";

    String[] columns() default {};

    boolean unique() default false;

    String where() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@interface VictorIndexes {
    VictorIndex[] value();
}