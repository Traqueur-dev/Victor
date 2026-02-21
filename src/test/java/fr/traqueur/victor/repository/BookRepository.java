package fr.traqueur.victor.repository;

import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.dto.BookDto;
import fr.traqueur.victor.entities.Book;

import java.util.List;

public interface BookRepository extends Repository<BookDto, Book, Long> {

    List<BookDto> findByTitle(String title);
}