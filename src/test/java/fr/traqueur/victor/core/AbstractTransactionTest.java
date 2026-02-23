package fr.traqueur.victor.core;

import fr.traqueur.victor.entities.transaction.Transaction;
import fr.traqueur.victor.exceptions.VictorTransactionException;
import fr.traqueur.victor.dto.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractTransactionTest extends AbstractVictorTest {

    @BeforeEach
    void cleanUpBeforeTransactionTest() {
        userRepository.deleteAll();
        authorRepo.deleteAll();
        bookRepo.deleteAll();
    }

    @Test
    void testTransactionCommit() {
        victor.transaction(() -> {
            userRepository.save(
                    new UserDto(null,
                            "tx_" + System.nanoTime(),
                            "tx@test.com", 25, true, "TX"));
        });

        assertTrue(userRepository.count() > 0);
    }

    @Test
    void testTransactionRollback() {
        assertThrows(RuntimeException.class, () -> {
            victor.transaction(() -> {
                userRepository.save(
                        new UserDto(null,
                                "rollback_" + System.nanoTime(),
                                "rb@test.com", 25, true, "RB"));
                throw new RuntimeException("fail");
            });
        });

        assertEquals(0, userRepository.count());
    }

    @Test
    void testAutoRollbackOnClose() {
        Transaction tx = victor.beginTransaction();

        userRepository.save(
                new UserDto(null,
                        "autorollback",
                        "auto@example.com",
                        25,
                        true,
                        "Auto Rollback")
        );

        // close sans commit → rollback
        tx.close();

        assertEquals(0, userRepository.count());
        Optional<UserDto> found = userRepository.findByUsername("autorollback");
        assertFalse(found.isPresent());
    }

    @Test
    void testNestedTransactionThrowsException() {
        assertThrows(VictorTransactionException.class, () -> {
            victor.transaction(() -> {

                userRepository.save(
                        new UserDto(null,
                                "outer",
                                "outer@example.com",
                                25,
                                true,
                                "Outer")
                );

                // Nested transaction
                victor.transaction(() -> {
                    userRepository.save(
                            new UserDto(null,
                                    "inner",
                                    "inner@example.com",
                                    30,
                                    true,
                                    "Inner")
                    );
                });
            });
        });

        // Rien ne doit être commit
        assertEquals(0, userRepository.count());
    }

    @Test
    void testTransactionUpdateAndCommit() {

        UserDto saved = userRepository.save(
                new UserDto(null,
                        "isolated",
                        "isolated@example.com",
                        25,
                        true,
                        "Isolated User")
        );

        victor.transaction(() -> {
            Optional<UserDto> user = userRepository.findById(saved.id());
            assertTrue(user.isPresent());

            UserDto updated = new UserDto(
                    user.get().id(),
                    user.get().username(),
                    "updated@example.com",
                    30,
                    user.get().active(),
                    user.get().name()
            );

            userRepository.save(updated);

            Optional<UserDto> inTx = userRepository.findById(saved.id());
            assertEquals("updated@example.com", inTx.get().email());
            assertEquals(30, inTx.get().age());
        });

        Optional<UserDto> afterCommit = userRepository.findById(saved.id());

        assertTrue(afterCommit.isPresent());
        assertEquals("updated@example.com", afterCommit.get().email());
        assertEquals(30, afterCommit.get().age());
    }
}