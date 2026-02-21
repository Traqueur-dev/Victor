package fr.traqueur.victor.core;

import fr.traqueur.victor.dto.AuthorDto;
import fr.traqueur.victor.dto.BookDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractRelationshipTest extends AbstractVictorTest {

    @Test
    void testManyToOne() {
        AuthorDto author = authorRepo.save(
                new AuthorDto(null, "George Orwell", "UK", List.of()));

        BookDto book = bookRepo.save(
                new BookDto(null, "1984", author));

        BookDto loaded = bookRepo.findById(book.id()).orElseThrow();

        assertNotNull(loaded.author());
        assertEquals(author.id(), loaded.author().id());
    }

    @Test
    void testOneToMany() {
        AuthorDto author = authorRepo.save(
                new AuthorDto(null, "Isaac Asimov", "USA", List.of()));

        bookRepo.save(new BookDto(null, "Foundation", author));
        bookRepo.save(new BookDto(null, "I, Robot", author));

        AuthorDto loaded = authorRepo.findById(author.id()).orElseThrow();

        assertEquals(2, loaded.books().size());
    }
}