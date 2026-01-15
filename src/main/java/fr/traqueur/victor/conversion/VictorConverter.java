package fr.traqueur.victor.conversion;

import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.exceptions.VictorConversionException;

import java.lang.reflect.*;
import java.time.LocalDateTime;
import java.util.Arrays;

public final class VictorConverter {

    public static <DTO extends Dto<MODEL>, MODEL extends Entity<?>>
           DTO modelToDto(MODEL model, Class<DTO> dtoClass) {
        
        if (model == null) return null;

        try {
            Method method = dtoClass.getDeclaredMethod("fromModel", model.getClass());
            @SuppressWarnings("unchecked")
            DTO dto = (DTO) method.invoke(null, model);
            return dto;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            throw new VictorConversionException("Failed to convert model to DTO, DTO class must have fromModel(model) static method", ex);
        }
    }
}