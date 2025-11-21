package fr.traqueur.victor.annotations;

import fr.traqueur.victor.entities.Repository;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UseRepository {

    Class<? extends Repository<?, ?, ?>> value();
}