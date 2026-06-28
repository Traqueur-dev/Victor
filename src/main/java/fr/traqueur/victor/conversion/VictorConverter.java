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

    /**
     * Validates (at startup) that {@code entityClass} declares the
     * {@code static fromModel(<Model>)} companion required for model conversion.
     * Throws {@link VictorConversionException} with an actionable message otherwise.
     */
    public static void assertConvertible(Class<?> entityClass) {
        Class<?> modelClass = resolveModelClass(entityClass);
        Method method;
        try {
            method = entityClass.getDeclaredMethod("fromModel", modelClass);
        } catch (NoSuchMethodException e) {
            throw new VictorConversionException(entityClass.getSimpleName()
                    + " must declare a 'public static " + entityClass.getSimpleName()
                    + " fromModel(" + modelClass.getSimpleName() + ")' method");
        }
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new VictorConversionException(entityClass.getSimpleName()
                    + ".fromModel(" + modelClass.getSimpleName() + ") must be static");
        }
    }

    private static Class<?> resolveModelClass(Class<?> entityClass) {
        for (Type itf : entityClass.getGenericInterfaces()) {
            if (itf instanceof ParameterizedType pt && pt.getRawType() == Entity.class) {
                if (pt.getActualTypeArguments()[0] instanceof Class<?> modelClass) {
                    return modelClass;
                }
            }
        }
        throw new VictorConversionException("Cannot resolve the Model type of entity "
                + entityClass.getSimpleName() + " (it must implement Entity<Model>)");
    }
}