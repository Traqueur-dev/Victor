package fr.traqueur.victor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container of the {@link java.lang.annotation.Repeatable} {@link VictorIndex}. Never written by
 * hand — the compiler generates it when {@code @VictorIndex} is repeated. It must be public
 * because that generated container lives in the <em>consumer's</em> class file: a package-private
 * container makes repeating {@code @VictorIndex} uncompilable outside this package.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface VictorIndexes {
    VictorIndex[] value();
}