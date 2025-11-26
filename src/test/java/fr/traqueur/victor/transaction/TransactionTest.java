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
    void testTransactionCommit() {
        VictorLogger.info("\n--- Test 1: Transaction Commit ---");
        
        // Exécuter dans une transaction
        victor.transaction(() -> {
            UserDto user1 = new UserDto(null, "user1", "user1@example.com", 25, true, "User One");
            userRepository.save(user1);
            
            UserDto user2 = new UserDto(null, "user2", "user2@example.com", 30, true, "User Two");
            userRepository.save(user2);
            
            VictorLogger.info("✓ 2 users saved in transaction");
        });
        
        // Vérifier que les données sont bien présentes
        long count = userRepository.count();
        assertEquals(2, count, "Should have 2 users after commit");
        
        Optional<UserDto> user1 = userRepository.findByUsername("user1");
        assertTrue(user1.isPresent(), "User1 should exist");
        
        Optional<UserDto> user2 = userRepository.findByUsername("user2");
        assertTrue(user2.isPresent(), "User2 should exist");
        
        VictorLogger.info("✓ Transaction committed successfully");
    }
    
    @Test
    @Order(2)
    void testTransactionRollback() {
        VictorLogger.info("\n--- Test 2: Transaction Rollback ---");
        
        // Sauvegarder un utilisateur avant la transaction
        UserDto existingUser = new UserDto(null, "existing", "existing@example.com", 20, true, "Existing User");
        userRepository.save(existingUser);
        
        long countBefore = userRepository.count();
        assertEquals(1, countBefore, "Should have 1 user before transaction");
        
        // Exécuter une transaction qui échoue
        assertThrows(VictorTransactionException.class, () -> {
            victor.transaction(() -> {
                UserDto user1 = new UserDto(null, "user1", "user1@example.com", 25, true, "User One");
                userRepository.save(user1);
                
                VictorLogger.info("✓ User1 saved");
                
                // Lever une exception pour provoquer un rollback
                throw new RuntimeException("Simulated error");
            });
        });
        
        // Vérifier que le rollback a fonctionné
        long countAfter = userRepository.count();
        assertEquals(1, countAfter, "Should still have only 1 user after rollback");
        
        Optional<UserDto> user1 = userRepository.findByUsername("user1");
        assertFalse(user1.isPresent(), "User1 should NOT exist after rollback");
        
        Optional<UserDto> existing = userRepository.findByUsername("existing");
        assertTrue(existing.isPresent(), "Existing user should still exist");
        
        VictorLogger.info("✓ Transaction rolled back successfully");
    }
    
    @Test
    @Order(3)
    void testTransactionWithReturn() {
        VictorLogger.info("\n--- Test 3: Transaction with Return Value ---");
        
        // Exécuter une transaction avec retour
        UserDto savedUser = victor.transaction(() -> {
            UserDto user = new UserDto(null, "john_doe", "john@example.com", 28, true, "John Doe");
            return userRepository.save(user);
        });
        
        assertNotNull(savedUser, "Saved user should not be null");
        assertNotNull(savedUser.id(), "Saved user should have an ID");
        assertEquals("john_doe", savedUser.username());
        
        // Vérifier que l'utilisateur existe en base
        Optional<UserDto> found = userRepository.findById(savedUser.id());
        assertTrue(found.isPresent(), "User should exist in database");
        
        VictorLogger.info("✓ Transaction with return value successful");
        VictorLogger.info("  Returned: " + savedUser);
    }

    @Test
    @Order(4)
    void testManualTransaction() {
        VictorLogger.info("\n--- Test 4: Manual Transaction ---");

        Transaction tx = victor.beginTransaction();
        try {
            UserDto user1 = new UserDto(null, "manual1", "manual1@example.com", 25, true, "Manual One");
            userRepository.save(user1);

            UserDto user2 = new UserDto(null, "manual2", "manual2@example.com", 30, true, "Manual Two");
            userRepository.save(user2);

            VictorLogger.info("✓ 2 users saved in manual transaction");

            tx.commit();
            VictorLogger.info("✓ Manual commit successful");

        } catch (Exception e) {
            tx.rollback();
            throw e;
        } finally {
            tx.close();
        }

        // Vérifier IMMÉDIATEMENT après la transaction
        long count = userRepository.count();
        assertEquals(2, count, "Should have 2 users after manual commit");

        VictorLogger.info("✓ Manual transaction completed");
    }
    
    @Test
    @Order(5)
    void testManualTransactionRollback() {
        VictorLogger.info("\n--- Test 5: Manual Transaction Rollback ---");
        
        Transaction tx = victor.beginTransaction();
        try {
            UserDto user1 = new UserDto(null, "rollback1", "rollback1@example.com", 25, true, "Rollback One");
            userRepository.save(user1);
            
            VictorLogger.info("✓ User saved in manual transaction");

            tx.rollback();
            VictorLogger.info("✓ Manual rollback executed");
            
        } finally {
            tx.close();
        }

        long count = userRepository.count();
        assertEquals(0, count, "Should have 0 users after manual rollback");
        
        Optional<UserDto> user = userRepository.findByUsername("rollback1");
        assertFalse(user.isPresent(), "User should NOT exist after rollback");
        
        VictorLogger.info("✓ Manual rollback successful");
    }
    
    @Test
    @Order(6)
    void testAutoRollbackOnClose() {
        VictorLogger.info("\n--- Test 6: Auto-Rollback on Close ---");
        
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
    @Order(7)
    void testNestedTransactionThrowsException() {
        VictorLogger.info("\n--- Test 7: Nested Transaction (Should Fail) ---");
        
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
    @Order(8)
    void testTransactionIsolation() {
        VictorLogger.info("\n--- Test 8: Transaction Isolation ---");
        
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
    
    @Test
    @Order(9)
    void testMultipleOperationsInTransaction() {
        VictorLogger.info("\n--- Test 9: Multiple Operations in Transaction ---");
        
        victor.transaction(() -> {
            UserDto user1 = new UserDto(null, "user1", "user1@example.com", 25, true, "User One");
            UserDto saved1 = userRepository.save(user1);
            VictorLogger.info("✓ User1 inserted");

            UserDto user2 = new UserDto(null, "user2", "user2@example.com", 30, true, "User Two");
            UserDto saved2 = userRepository.save(user2);
            VictorLogger.info("✓ User2 inserted");

            UserDto updated = new UserDto(
                saved1.id(),
                saved1.username(),
                "updated1@example.com",
                26,
                saved1.active(),
                saved1.name()
            );
            userRepository.save(updated);
            VictorLogger.info("✓ User1 updated");

            Optional<UserDto> found = userRepository.findByUsername("user2");
            assertTrue(found.isPresent(), "User2 should be found");
            VictorLogger.info("✓ User2 found");

            long count = userRepository.count();
            assertEquals(2, count, "Should have 2 users");
            VictorLogger.info("✓ Count = " + count);
        });

        long finalCount = userRepository.count();
        assertEquals(2, finalCount, "Should still have 2 users after commit");
        
        Optional<UserDto> user1 = userRepository.findByUsername("user1");
        assertTrue(user1.isPresent());
        assertEquals("updated1@example.com", user1.get().email());
        assertEquals(26, user1.get().age());
        
        VictorLogger.info("✓ Multiple operations in transaction successful");
    }
}