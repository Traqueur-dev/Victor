package fr.traqueur.victor.core;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.VictorBuilder;
import fr.traqueur.victor.entity.AuthorEntity;
import fr.traqueur.victor.entity.BookEntity;
import fr.traqueur.victor.entity.transaction.Transaction;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.repository.AuthorRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Connection-pool behaviour, exercised against every dialect.
 *
 * <p>Unlike the other abstract tests, this one does <b>not</b> extend {@link AbstractVictorTest}:
 * it builds its own {@link Victor} with a tiny {@code maximumPoolSize}. The pool is memoized per
 * {@code connectionUrl}, so letting {@code AbstractVictorTest.setUpBase()} build first would create
 * a default-sized pool and make the override a no-op on fixed-URL dialects (MySQL, PostgreSQL).
 * Each test here closes its Victor in a {@code finally} (pool refcount → 0 → evicted), so the next
 * build re-creates the pool with the requested size.</p>
 */
public abstract class AbstractConnectionPoolTest {

    protected abstract VictorBuilder configureVictor();

    @Test
    void testMaximumPoolSizePropertyIsApplied() {
        // Regression: .property("maximumPoolSize", ...) used to be forwarded as a driver
        // DataSource property (and dropped entirely by the defaults-wrapped forEach), so the
        // pool silently stayed at the hardcoded default of 20. Cap it at 1 and prove it holds:
        // while one connection is held by a transaction, a second thread cannot get one.
        Victor victor = configureVictor()
                .autoMigrate()
                .property("maximumPoolSize", "1")
                .property("connectionTimeout", "1500")
                .entities(AuthorEntity.class, BookEntity.class)
                .build();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            AuthorRepository authorRepo = victor.createRepository(AuthorRepository.class);
            Transaction tx = victor.beginTransaction();
            try {
                // Force the single pooled connection to be checked out and held by this tx.
                authorRepo.count();

                // A second, independent thread must fail to obtain a connection (pool is full at 1)
                // and time out after connectionTimeout — with the old default of 20 it would succeed.
                Future<Long> blocked = worker.submit(authorRepo::count);
                ExecutionException thrown = assertThrows(ExecutionException.class,
                        () -> blocked.get(10, TimeUnit.SECONDS));
                assertInstanceOf(VictorException.class, thrown.getCause(), thrown.toString());
            } finally {
                tx.close();
            }
        } finally {
            worker.shutdownNow();
            victor.close();
        }
    }

    @Test
    void testEagerGraphLoadsOnSingleConnection() {
        // Regression for the reported deadlock: reading an author eagerly loaded its books on a
        // *second* connection while the author's connection was still held for mapping. With a
        // one-connection pool that self-deadlocked and timed out. A short connectionTimeout makes
        // the failure fast (2s) instead of the 30s default; the fix reuses the one connection.
        Victor victor = configureVictor()
                .autoMigrate()
                .property("maximumPoolSize", "1")
                .property("connectionTimeout", "2000")
                .entities(AuthorEntity.class, BookEntity.class)
                .build();
        try {
            AuthorRepository authorRepo = victor.createRepository(AuthorRepository.class);

            // save() is transactional (one connection), so the write side is fine at pool size 1.
            AuthorEntity author = authorRepo.save(
                    new AuthorEntity(null, "Isaac Asimov", "USA", List.of(
                            new BookEntity(null, "Foundation", null),
                            new BookEntity(null, "I, Robot", null))));

            // The read is NOT transactional: loading the author then EAGER-loading its books must
            // reuse the same connection, otherwise the one-connection pool is exhausted.
            AuthorEntity loaded = authorRepo.findById(author.id()).orElseThrow();
            assertEquals(2, loaded.books().size());
        } finally {
            victor.close();
        }
    }
}
