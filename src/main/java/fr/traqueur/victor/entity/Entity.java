package fr.traqueur.victor.entity;

public interface Entity<MODEL extends Model<?>> {

    MODEL toModel();

    default boolean isValid() {
        return true;
    }

    static <D extends Entity<M>, M extends Model<?>> D fromModel(M model, Class<D> entityClass) {
        throw new UnsupportedOperationException("Automatic conversion from model to E is implemented by Victor.");
    }
}
