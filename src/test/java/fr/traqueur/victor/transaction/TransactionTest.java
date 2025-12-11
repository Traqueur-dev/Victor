package fr.traqueur.victor.transaction;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.entities.transaction.Transaction;
import fr.traqueur.victor.exceptions.VictorTransactionException;
import fr.traqueur.victor.test.dto.UserDto;
import fr.traqueur.victor.test.entities.User;
import fr.traqueur.victor.test.repository.UserRepository;
import fr.traqueur.victor.utils.VictorLogger;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case and advanced transaction tests.
 * Common transaction tests are in AbstractDialectTest and run on all dialects.
 * This class contains only H2-specific edge cases and advanced scenarios.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionTest {

    private static Victor victor;
    private static UserRepository userRepository;

    @BeforeEach
    void setup() {
        if (victor != null) {
            try {
                victor.close();
            } catch (Exception ignored) {}
        }

        String dbName = "txtest_" + System.nanoTime();
        VictorLogger.info("\n[TEST] Creating new database: " + dbName);

        victor = Victor.configure()
                .h2()
                .database(dbName)
                .showSql()
                .autoMigrate()
                .entities(User.class)
                .property("DB_CLOSE_DELAY", "-1")
                .property("DB_CLOSE_ON_EXIT", "TRUE")
                .build();

        userRepository = victor.createRepository(UserRepository.class);
    }

    @AfterEach
    void tearDown() {
        if (victor != null) {
            victor.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Edge case: Auto-rollback on transaction close")
    void testAutoRollbackOnClose() {
        VictorLogger.info("\n--- Edge Case: Auto-Rollback on Close ---");

        Transaction tx = victor.beginTransaction();

        UserDto user = new UserDto(null, "autorollback", "auto@example.com", 25, true, "Auto Rollback");
        userRepository.save(user);

        VictorLogger.info("✓ User saved in transaction");

        // Fermer sans commit → rollback automatique
        tx.close();
        VictorLogger.info("✓ Transaction closed without commit");

        // Vérifier que les données ne sont pas présentes
        long count = userRepository.count();
        assertEquals(0, count, "Should have 0 users after auto-rollback");

        Optional<UserDto> found = userRepository.findByUsername("autorollback");
        assertFalse(found.isPresent(), "User should NOT exist after auto-rollback");

        VictorLogger.info("✓ Auto-rollback on close successful");
    }

    @Test
    @Order(2)
    @DisplayName("Edge case: Nested transactions should throw exception")
    void testNestedTransactionThrowsException() {
        VictorLogger.info("\n--- Edge Case: Nested Transaction (Should Fail) ---");

        // Les transactions imbriquées ne sont pas supportées
        assertThrows(VictorTransactionException.class, () -> {
            victor.transaction(() -> {
                UserDto user1 = new UserDto(null, "outer", "outer@example.com", 25, true, "Outer");
                userRepository.save(user1);

                // Essayer de créer une transaction imbriquée
                victor.transaction(() -> {
                    UserDto user2 = new UserDto(null, "inner", "inner@example.com", 30, true, "Inner");
                    userRepository.save(user2);
                });
            });
        });

        VictorLogger.info("✓ Nested transaction correctly rejected");
    }

    @Test
    @Order(3)
    @DisplayName("Edge case: Transaction isolation")
    void testTransactionIsolation() {
        VictorLogger.info("\n--- Edge Case: Transaction Isolation ---");

        // Créer un utilisateur
        UserDto initialUser = new UserDto(null, "isolated", "isolated@example.com", 25, true, "Isolated User");
        UserDto saved = userRepository.save(initialUser);

        VictorLogger.info("✓ Initial user created with ID: " + saved.id());

        victor.transaction(() -> {
            Optional<UserDto> user = userRepository.findById(saved.id());
            assertTrue(user.isPresent(), "User should exist in transaction");

            UserDto updated = new UserDto(
                user.get().id(),
                user.get().username(),
                "updated@example.com",
                30,
                user.get().active(),
                user.get().name()
            );

            userRepository.save(updated);
            VictorLogger.info("✓ User updated in transaction");

            Optional<UserDto> inTx = userRepository.findById(saved.id());
            assertEquals("updated@example.com", inTx.get().email());
            assertEquals(30, inTx.get().age());
        });

        // Vérifier après commit
        Optional<UserDto> afterCommit = userRepository.findById(saved.id());
        assertTrue(afterCommit.isPresent(), "User should exist after commit");
        assertEquals("updated@example.com", afterCommit.get().email());
        assertEquals(30, afterCommit.get().age());

        VictorLogger.info("✓ Transaction isolation working correctly");
    }
}