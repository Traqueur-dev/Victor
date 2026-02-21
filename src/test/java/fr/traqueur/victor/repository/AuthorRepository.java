package fr.traqueur.victor.repository;

import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.dto.AuthorDto;
import fr.traqueur.victor.entities.Author;

import java.util.List;

public interface AuthorRepository extends Repository<AuthorDto, Author, Long> {

    List<AuthorDto> findByName(String name);

    List<AuthorDto> findByCountry(String country);
}