package fr.traqueur.victor.core;

import fr.traqueur.victor.entity.AuthorEntity;
import fr.traqueur.victor.entity.BookEntity;
import fr.traqueur.victor.entity.CourseEntity;
import fr.traqueur.victor.entity.PassportEntity;
import fr.traqueur.victor.entity.PersonEntity;
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

    @Test
    void testCascadePersistManyToOne() {
        // Save a book referencing a brand-new (unsaved) author: the author is
        // cascade-persisted and the book FK is wired to its generated id.
        BookEntity book = bookRepo.save(
                new BookEntity(null, "Brave New World",
                        new AuthorEntity(null, "Aldous Huxley", "UK", List.of())));

        assertNotNull(book.id());
        assertNotNull(book.author());
        assertNotNull(book.author().id());

        AuthorEntity savedAuthor = authorRepo.findById(book.author().id()).orElseThrow();
        assertEquals("Aldous Huxley", savedAuthor.name());

        BookEntity loaded = bookRepo.findById(book.id()).orElseThrow();
        assertNotNull(loaded.author());
        assertEquals(book.author().id(), loaded.author().id());
    }

    @Test
    void testOneToOneOwningAndInverse() {
        PersonEntity person = personRepo.save(new PersonEntity(null, "Ada Lovelace", null));
        PassportEntity passport = passportRepo.save(new PassportEntity(null, "P-12345", person));

        // owning side loads the related person via the FK column
        PassportEntity loadedPassport = passportRepo.findById(passport.id()).orElseThrow();
        assertNotNull(loadedPassport.person());
        assertEquals(person.id(), loadedPassport.person().id());

        // inverse side loads the passport via mappedBy
        PersonEntity loadedPerson = personRepo.findById(person.id()).orElseThrow();
        assertNotNull(loadedPerson.passport());
        assertEquals(passport.id(), loadedPerson.passport().id());
    }

    @Test
    void testCascadePersistOneToMany() {
        // Save an author with brand-new children: each book is cascade-persisted
        // with its FK pointing back to the parent author.
        AuthorEntity author = authorRepo.save(
                new AuthorEntity(null, "Frank Herbert", "USA", List.of(
                        new BookEntity(null, "Dune", null),
                        new BookEntity(null, "Dune Messiah", null))));

        assertNotNull(author.id());

        AuthorEntity loaded = authorRepo.findById(author.id()).orElseThrow();
        assertEquals(2, loaded.books().size());
    }

    @Test
    void testCascadeSyncUpdatesExistingAndRemovesOrphans() {
        // Save two children, reload (so children carry their ids), then re-save a mutated set:
        // one child updated, one dropped (orphan), one added.
        AuthorEntity author = authorRepo.save(
                new AuthorEntity(null, "Terry Pratchett", "UK", List.of(
                        new BookEntity(null, "Guards! Guards!", null),
                        new BookEntity(null, "Mort", null))));
        Long authorId = author.id();

        AuthorEntity loaded = authorRepo.findById(authorId).orElseThrow();
        assertEquals(2, loaded.books().size());
        BookEntity kept = loaded.books().stream()
                .filter(b -> b.title().equals("Mort")).findFirst().orElseThrow();
        Long droppedId = loaded.books().stream()
                .filter(b -> b.title().equals("Guards! Guards!")).findFirst().orElseThrow().id();

        // same id -> UPDATE ; null id -> INSERT ; "Guards! Guards!" absent -> orphan DELETE
        authorRepo.save(new AuthorEntity(authorId, "Terry Pratchett", "UK", List.of(
                new BookEntity(kept.id(), "Mort (revised)", null),
                new BookEntity(null, "Reaper Man", null))));

        AuthorEntity reloaded = authorRepo.findById(authorId).orElseThrow();
        assertEquals(2, reloaded.books().size(), "updated + added, no duplicate");
        assertTrue(reloaded.books().stream().anyMatch(b -> b.title().equals("Mort (revised)")));
        assertTrue(reloaded.books().stream().anyMatch(b -> b.title().equals("Reaper Man")));
        assertTrue(bookRepo.findById(droppedId).isEmpty(), "orphan row deleted");
    }

    @Test
    void testCascadeDeleteRemovesChildren() {
        AuthorEntity author = authorRepo.save(
                new AuthorEntity(null, "Douglas Adams", "UK", List.of(
                        new BookEntity(null, "The Hitchhiker's Guide", null),
                        new BookEntity(null, "The Restaurant at the End of the Universe", null))));
        Long authorId = author.id();
        List<Long> bookIds = authorRepo.findById(authorId).orElseThrow()
                .books().stream().map(BookEntity::id).toList();
        assertEquals(2, bookIds.size());

        // Deleting the parent cascade-deletes its children (FK-safe), no manual child cleanup.
        authorRepo.deleteById(authorId);

        assertTrue(authorRepo.findById(authorId).isEmpty());
        for (Long bookId : bookIds) {
            assertTrue(bookRepo.findById(bookId).isEmpty(), "child book deleted on cascade");
        }
    }
}