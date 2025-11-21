package fr.traqueur.victor.entities;

import java.util.List;
import java.util.Optional;

public interface Service<MODEL extends Entity<ID>, DTO extends Dto<MODEL>, ID, REPO extends Repository<DTO, MODEL, ID>> {

    MODEL save(MODEL model);

    Optional<MODEL> findById(ID id);

    List<MODEL> findAll();

    MODEL update(ID id, MODEL model);

    void deleteById(ID id);

    void delete(MODEL model);

    boolean exists(ID id);

    long count();

    default boolean isValid(MODEL model) {
        return model != null && model.isValid();
    }

    default MODEL validateAndSave(MODEL model) {
        if (!isValid(model)) {
            throw new IllegalArgumentException("Entité invalide : " + model);
        }
        return save(model);
    }

    List<MODEL> saveAll(List<MODEL> models);

    void deleteAll(List<ID> ids);

    REPO repository();
}