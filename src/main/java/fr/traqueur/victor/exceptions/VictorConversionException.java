package fr.traqueur.victor.exceptions;

public class VictorConversionException extends VictorException {
    
    private final Class<?> sourceType;
    private final Class<?> targetType;

    public VictorConversionException(String message) {
        super(message);
        this.sourceType = null;
        this.targetType = null;
    }

    public VictorConversionException(String message, Throwable cause) {
        super(message, cause);
        this.sourceType = null;
        this.targetType = null;
    }

    public VictorConversionException(Class<?> sourceType, Class<?> targetType, String message) {
        super(String.format("Unable to convert %s to %s : %s",
              sourceType.getSimpleName(), targetType.getSimpleName(), message));
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    public VictorConversionException(Class<?> sourceType, Class<?> targetType, String message, Throwable cause) {
        super(String.format("Unable to convert %s to %s : %s",
              sourceType.getSimpleName(), targetType.getSimpleName(), message), cause);
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    public Class<?> getSourceType() {
        return sourceType;
    }

    public Class<?> getTargetType() {
        return targetType;
    }
}