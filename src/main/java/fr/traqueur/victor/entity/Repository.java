package fr.traqueur.victor.entity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface Repository<E extends Entity<MODEL>, MODEL extends Model<ID>, ID> {

    E save(E entity);

    Optional<E> findById(ID id);

    List<E> findAll();

    void deleteById(ID id);

    void delete(E entity);

    boolean existsById(ID id);

    long count();

    Collection<E> saveAll(Collection<E> entities);

    void deleteAll(Collection<E> entities);

    void deleteAll();

    Query<E> query();
}
