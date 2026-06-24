package fr.traqueur.victor.core;

import fr.traqueur.victor.entity.AuthorEntity;
import fr.traqueur.victor.entity.BookEntity;
import fr.traqueur.victor.entity.CourseEntity;
import fr.traqueur.victor.entity.StudentEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractRelationshipTest extends AbstractVictorTest {

    @Test
    void testManyToOne() {
        AuthorEntity author = authorRepo.save(
                new AuthorEntity(null, "George Orwell", "UK", List.of()));

        BookEntity book = bookRepo.save(
                new BookEntity(null, "1984", author));

        BookEntity loaded = bookRepo.findById(book.id()).orElseThrow();

        assertNotNull(loaded.author());
        assertEquals(author.id(), loaded.author().id());
    }

    @Test
    void testOneToMany() {
        AuthorEntity author = authorRepo.save(
                new AuthorEntity(null, "Isaac Asimov", "USA", List.of()));

        bookRepo.save(new BookEntity(null, "Foundation", author));
        bookRepo.save(new BookEntity(null, "I, Robot", author));

        AuthorEntity loaded = authorRepo.findById(author.id()).orElseThrow();

        assertEquals(2, loaded.books().size());
    }

    @Test
    void testManyToMany() {
        CourseEntity course1 = courseRepo.save(new CourseEntity(null, "Mathematics"));
        CourseEntity course2 = courseRepo.save(new CourseEntity(null, "Physics"));

        StudentEntity student = studentRepo.save(
                new StudentEntity(null, "Alice", List.of(course1, course2)));

        StudentEntity loaded = studentRepo.findById(student.id()).orElseThrow();

        assertEquals(2, loaded.courses().size());
        assertTrue(loaded.courses().stream().anyMatch(c -> c.id().equals(course1.id())));
        assertTrue(loaded.courses().stream().anyMatch(c -> c.id().equals(course2.id())));
    }
}