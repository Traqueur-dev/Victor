package fr.traqueur.victor.core;

import fr.traqueur.victor.dto.AuthorDto;
import fr.traqueur.victor.dto.BookDto;
import fr.traqueur.victor.dto.CourseDto;
import fr.traqueur.victor.dto.StudentDto;
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

    @Test
    void testManyToMany() {
        CourseDto course1 = courseRepo.save(new CourseDto(null, "Mathematics"));
        CourseDto course2 = courseRepo.save(new CourseDto(null, "Physics"));

        StudentDto student = studentRepo.save(
                new StudentDto(null, "Alice", List.of(course1, course2)));

        StudentDto loaded = studentRepo.findById(student.id()).orElseThrow();

        assertEquals(2, loaded.courses().size());
        assertTrue(loaded.courses().stream().anyMatch(c -> c.id().equals(course1.id())));
        assertTrue(loaded.courses().stream().anyMatch(c -> c.id().equals(course2.id())));
    }
}