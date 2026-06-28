package fr.traqueur.victor.repository;

import fr.traqueur.victor.entity.Repository;
import fr.traqueur.victor.entity.AuthorEntity;
import fr.traqueur.victor.model.Author;

import java.util.List;

public interface AuthorRepository extends Repository<AuthorEntity, Author, Long> {

    List<AuthorEntity> findByName(String name);

    List<AuthorEntity> findByCountry(String country);
}