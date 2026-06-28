package fr.traqueur.victor.exceptions;

import java.util.List;

public class VictorValidationException extends VictorException {
    
    private final List<String> validationErrors;
    private final Object invalidObject;

    public VictorValidationException(String message) {
        super(message);
        this.validationErrors = List.of(message);
        this.invalidObject = null;
    }

    public VictorValidationException(List<String> validationErrors) {
        super("Errors with validation : " + String.join(", ", validationErrors));
        this.validationErrors = List.copyOf(validationErrors);
        this.invalidObject = null;
    }

    public VictorValidationException(Object invalidObject, List<String> validationErrors) {
        super(String.format("Validation failed for %s : %s",
              invalidObject.getClass().getSimpleName(), 
              String.join(", ", validationErrors)));
        this.validationErrors = List.copyOf(validationErrors);
        this.invalidObject = invalidObject;
    }

    public VictorValidationException(Object invalidObject, String message) {
        super(String.format("Validation failed for %s : %s",
              invalidObject.getClass().getSimpleName(), message));
        this.validationErrors = List.of(message);
        this.invalidObject = invalidObject;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public Object getInvalidObject() {
        return invalidObject;
    }

    public boolean hasMultipleErrors() {
        return validationErrors.size() > 1;
    }
}