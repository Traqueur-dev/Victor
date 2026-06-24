package fr.traqueur.victor.service;

import fr.traqueur.victor.entity.Service;
import fr.traqueur.victor.entity.UserEntity;
import fr.traqueur.victor.model.User;
import fr.traqueur.victor.repository.UserRepository;

import java.util.List;
import java.util.Optional;

public interface UserService extends Service<User, UserEntity, Long, UserRepository> {

    // Custom methods delegated to the repository method of the same signature,
    // returning models instead of entities.

    // Dynamic finder
    Optional<User> findByUsername(String username);

    // Dynamic finder returning a list
    List<User> findByAgeGreaterThan(int age);

    // @Query (named parameters) declared on the repository — the service does
    // not need to redeclare the annotation, only the matching signature.
    Optional<User> findByUsernameCustom(String username);

    // Scalar return: passed through unchanged
    long countByActive(boolean active);
}
