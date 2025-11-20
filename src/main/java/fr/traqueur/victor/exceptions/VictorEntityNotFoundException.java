package fr.traqueur.victor.exceptions;

public class VictorEntityNotFoundException extends VictorException {
    
    private final Class<?> entityType;
    private final Object entityId;

    public VictorEntityNotFoundException(Class<?> entityType, Object entityId) {
        super(String.format("Entity %s with ID '%s' not found.",
              entityType.getSimpleName(), entityId));
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public VictorEntityNotFoundException(String message) {
        super(message);
        this.entityType = null;
        this.entityId = null;
    }

    public VictorEntityNotFoundException(Class<?> entityType, Object entityId, String message) {
        super(message);
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public Class<?> getEntityType() {
        return entityType;
    }

    public Object getEntityId() {
        return entityId;
    }
}