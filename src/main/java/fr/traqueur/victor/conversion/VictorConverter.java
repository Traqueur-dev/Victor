package fr.traqueur.victor.conversion;

import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.entity.Model;
import fr.traqueur.victor.exceptions.VictorConversionException;

import java.lang.reflect.*;
import java.time.LocalDateTime;
import java.util.Arrays;

public final class VictorConverter {

    public static <E extends Entity<MODEL>, MODEL extends Model<?>>
           E modelToEntity(MODEL model, Class<E> entityClass) {
        
        if (model == null) return null;

        try {
            Method method = entityClass.getDeclaredMethod("fromModel", model.getClass());
            @SuppressWarnings("unchecked")
            E entity = (E) method.invoke(null, model);
            return entity;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            throw new VictorConversionException("Failed to convert model to E, E class must have fromModel(model) static method", ex);
        }
    }
}