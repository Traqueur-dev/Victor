package fr.traqueur.victor.entities;

public interface Dto<MODEL extends Entity<?>> {

    MODEL toModel();

    default boolean isValid() {
        return true;
    }

    static <D extends Dto<M>, M extends Entity<?>> D fromModel(M model, Class<D> dtoClass) {
        throw new UnsupportedOperationException("Automatic conversion from model to DTO is implemented by Victor.");
    }
}
