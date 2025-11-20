package fr.traqueur.victor.entities;

public interface Entity<ID> {

    ID getId();

    void setId(ID id);

    default void beforeSave() {}

    default void afterSave() {}

    default void beforeDelete() {}

    default void afterDelete() {}

    default boolean isValid() {
        return true;
    }

    default boolean isNew() {
        return getId() == null;
    }
}
