package fr.traqueur.victor.repository;

import fr.traqueur.victor.entity.Repository;
import fr.traqueur.victor.entity.BookEntity;
import fr.traqueur.victor.model.Book;

import java.util.List;

public interface BookRepository extends Repository<BookEntity, Book, Long> {

    List<BookEntity> findByTitle(String title);
}