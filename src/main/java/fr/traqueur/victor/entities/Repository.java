package fr.traqueur.victor.entities;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface Repository<DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID> {

    DTO save(DTO dto);

    Optional<DTO> findById(ID id);

    List<DTO> findAll();

    void deleteById(ID id);

    void delete(DTO dto);

    boolean existsById(ID id);

    long count();

    Collection<DTO> saveAll(Collection<DTO> dtos);

    void deleteAll(Collection<DTO> dtos);

    void deleteAll();

    Query<DTO> query();
}
