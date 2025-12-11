package fr.traqueur.victor;

import fr.traqueur.victor.test.dto.UserDto;
import fr.traqueur.victor.test.entities.User;
import fr.traqueur.victor.test.repository.UserRepository;
import fr.traqueur.victor.test.service.UserService;
import fr.traqueur.victor.utils.VictorLogger;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base class for dialect-specific integration tests.
 *
 * <p>This class provides common test infrastructure for all database dialects:
 * <ul>
 *   <li>Automatic Victor instance setup and teardown</li>
 *   <li>Pre-configured User entity and repository</li>
 *   <li>Helper methods for common test operations</li>
 *   <li>Consistent test data creation</li>
 * </ul>
 *
 * <p>Subclasses must implement {@link #configureVictor()} to configure the Victor builder
 * for their specific dialect.
 *
 * <p>System properties support:
 * <ul>
 *   <li>{@code victor.test.dialect} - Dialect name (h2, mysql, postgresql, sqlite)</li>
 *   <li>{@code victor.test.url} - JDBC URL for the test database</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * public class H2DialectTest extends AbstractDialectTest {
 *     @Override
 *     protected VictorBuilder configureVictor() {
 *         return Victor.configure()
 *             .h2()
 *             .database("testdb_" + System.nanoTime());
 *     }
 *
 *     @Test
 *     void testH2SpecificFeature() {
 *         // H2-specific test
 *     }
 * }
 * }</pre>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractDialectTest {

    protected Victor victor;
    protected UserRepository userRepository;
    protected UserService userService;

    /**
     * Configures the VictorBuilder for the specific dialect to test.
     * This method is called during setup and should return a fully configured builder.
     *
     * <p>The builder should NOT call {@code .build()} - that will be done automatically.
     *
     * <p>Example:
     * <pre>{@code
     * @Override
     * protected VictorBuilder configureVictor() {
     *     return Victor.configure()
     *         .h2()
     *         .database("testdb");
     * }
     * }</pre>
     *
     * @return a configured VictorBuilder (without calling .build())
     */
    protected abstract VictorBuilder configureVictor();

    /**
     * Returns the name of the dialect being tested.
     * Default implementation reads from system property "victor.test.dialect".
     * Override this if needed.
     *
     * @return the dialect name (e.g., "h2", "mysql", "postgresql", "sqlite")
     */
    protected String getDialectName() {
        String dialect = System.getProperty("victor.test.dialect");
        return dialect != null ? dialect : "unknown";
    }

    /**
     * Configures and initializes the Victor instance before each test.
     * This method can be overridden to customize the setup.
     */
    @BeforeEach
    protected void setUp() {
        VictorLogger.info("\n========================================");
        VictorLogger.info("Setting up test for dialect: " + getDialectName());
        VictorLogger.info("========================================");

        VictorBuilder builder = configureVictor();

        // Apply common configuration
        builder.showSql()
               .autoMigrate()
               .entities(User.class);

        victor = builder.build();
        userRepository = victor.createRepository(UserRepository.class);
        userService = victor.createService(UserService.class);

        VictorLogger.info("✓ Victor initialized for " + getDialectName());
    }

    /**
     * Closes the Victor instance after each test.
     * This method can be overridden to customize the teardown.
     */
    @AfterEach
    protected void tearDown() {
        if (victor != null) {
            try {
                victor.close();
                VictorLogger.info("✓ Victor closed for " + getDialectName());
            } catch (Exception e) {
                VictorLogger.error("Error closing Victor", e);
            }
        }
        VictorLogger.info("========================================\n");
    }

    // ========================================
    // Helper Methods for Common Test Operations
    // ========================================

    /**
     * Creates a test user with the given username.
     *
     * @param username the username
     * @return a new User entity
     */
    protected User createUser(String username) {
        return new User(username, username + "@example.com", 25, true, "Test User " + username);
    }

    /**
     * Creates a test UserDto with the given username.
     *
     * @param username the username
     * @return a new UserDto
     */
    protected UserDto createUserDto(String username) {
        return new UserDto(null, username, username + "@example.com", 25, true, "Test User " + username);
    }

    /**
     * Creates and saves a test user.
     *
     * @param username the username
     * @return the saved UserDto
     */
    protected UserDto createAndSaveUser(String username) {
        UserDto dto = createUserDto(username);
        return userRepository.save(dto);
    }

    /**
     * Inserts standard test data (5 users) into the database.
     * Uses unique usernames with timestamp to avoid conflicts.
     */
    protected void insertStandardTestData() {
        VictorLogger.info("Inserting standard test data...");

        String suffix = "_" + System.nanoTime();

        userRepository.save(new UserDto(null, "john_doe" + suffix, "john@example.com", 25, true, "John Doe"));
        userRepository.save(new UserDto(null, "jane_smith" + suffix, "jane@example.com", 30, true, "Jane Smith"));
        userRepository.save(new UserDto(null, "bob_wilson" + suffix, "bob@example.com", 20, false, "Bob Wilson"));
        userRepository.save(new UserDto(null, "alice_brown" + suffix, null, 35, true, "Alice Brown"));
        userRepository.save(new UserDto(null, "charlie_jones" + suffix, "charlie@example.com", 28, true, "Charlie Jones"));

        VictorLogger.info("✓ Standard test data inserted (5 users)");
    }

    /**
     * Gets the Victor instance.
     *
     * @return the Victor instance
     */
    protected Victor getVictor() {
        return victor;
    }

    /**
     * Gets the UserRepository instance.
     *
     * @return the UserRepository instance
     */
    protected UserRepository getUserRepository() {
        return userRepository;
    }

    /**
     * Gets the UserService instance.
     *
     * @return the UserService instance
     */
    protected UserService getUserService() {
        return userService;
    }

    // ========================================
    // Common Tests - Run for ALL dialects
    // ========================================

    @Test
    @Order(1)
    @DisplayName("Common: Save and find user")
    void testSaveAndFind() {
        VictorLogger.info("\n=== Common Test: Save and Find ===");

        UserDto saved = createAndSaveUser("test_user_" + System.nanoTime());

        assertNotNull(saved);
        assertNotNull(saved.id());

        Optional<UserDto> found = userRepository.findById(saved.id());
        assertTrue(found.isPresent());
        assertEquals(saved.username(), found.get().username());
    }

    @Test
    @Order(2)
    @DisplayName("Common: Find all users")
    void testFindAll() {
        VictorLogger.info("\n=== Common Test: Find All ===");

        createAndSaveUser("user1_" + System.nanoTime());
        createAndSaveUser("user2_" + System.nanoTime());
        createAndSaveUser("user3_" + System.nanoTime());

        List<UserDto> all = userRepository.findAll();

        assertNotNull(all);
        assertTrue(all.size() >= 3);
    }

    @Test
    @Order(3)
    @DisplayName("Common: Update user")
    void testUpdate() {
        VictorLogger.info("\n=== Common Test: Update ===");

        UserDto saved = createAndSaveUser("update_test_" + System.nanoTime());

        UserDto updated = new UserDto(
                saved.id(),
                saved.username(),
                "updated@example.com",
                30,
                saved.active(),
                saved.name()
        );

        UserDto result = userRepository.save(updated);

        assertEquals("updated@example.com", result.email());
        assertEquals(30, result.age());
    }

    @Test
    @Order(4)
    @DisplayName("Common: Delete user")
    void testDelete() {
        VictorLogger.info("\n=== Common Test: Delete ===");

        UserDto saved = createAndSaveUser("delete_test_" + System.nanoTime());
        Long id = saved.id();

        assertTrue(userRepository.existsById(id));

        userRepository.deleteById(id);

        assertFalse(userRepository.existsById(id));
    }

    @Test
    @Order(5)
    @DisplayName("Common: Dynamic query - findByUsername")
    void testDynamicQueryFindByUsername() {
        VictorLogger.info("\n=== Common Test: Dynamic Query - findByUsername ===");

        String testUsername = "query_test_" + System.nanoTime();
        createAndSaveUser(testUsername);

        Optional<UserDto> result = userRepository.findByUsername(testUsername);

        assertTrue(result.isPresent());
        assertEquals(testUsername, result.get().username());
    }

    @Test
    @Order(6)
    @DisplayName("Common: Dynamic query - findByAgeGreaterThan")
    void testDynamicQueryAgeGreaterThan() {
        VictorLogger.info("\n=== Common Test: Dynamic Query - findByAgeGreaterThan ===");

        insertStandardTestData();

        List<UserDto> results = userRepository.findByAgeGreaterThan(25);

        assertFalse(results.isEmpty());
        results.forEach(user -> assertTrue(user.age() > 25));
    }

    @Test
    @Order(7)
    @DisplayName("Common: Transaction commit")
    void testTransactionCommit() {
        VictorLogger.info("\n=== Common Test: Transaction Commit ===");

        String user1Name = "tx_user1_" + System.nanoTime();
        String user2Name = "tx_user2_" + System.nanoTime();

        victor.transaction(() -> {
            createAndSaveUser(user1Name);
            createAndSaveUser(user2Name);
        });

        Optional<UserDto> user1 = userRepository.findByUsername(user1Name);
        Optional<UserDto> user2 = userRepository.findByUsername(user2Name);

        assertTrue(user1.isPresent());
        assertTrue(user2.isPresent());
    }

    @Test
    @Order(8)
    @DisplayName("Common: Transaction rollback")
    void testTransactionRollback() {
        VictorLogger.info("\n=== Common Test: Transaction Rollback ===");

        String rollbackUser = "rollback_user_" + System.nanoTime();

        assertThrows(Exception.class, () -> {
            victor.transaction(() -> {
                createAndSaveUser(rollbackUser);
                throw new RuntimeException("Simulated error");
            });
        });

        Optional<UserDto> user = userRepository.findByUsername(rollbackUser);
        assertFalse(user.isPresent());
    }

    // ========================================
    // Additional Dynamic Query Tests
    // ========================================

    @Test
    @Order(9)
    @DisplayName("Common: Dynamic query - findByUsernameAndEmail")
    void testFindByUsernameAndEmail() {
        VictorLogger.info("\n=== Common Test: Dynamic Query - findByUsernameAndEmail ===");

        String username = "and_test_" + System.nanoTime();
        String email = username + "@example.com";
        UserDto saved = new UserDto(null, username, email, 25, true, "And Test");
        userRepository.save(saved);

        Optional<UserDto> result = userRepository.findByUsernameAndEmail(username, email);

        assertTrue(result.isPresent());
        assertEquals(username, result.get().username());
    }

    @Test
    @Order(10)
    @DisplayName("Common: Dynamic query - findByUsernameOrEmail")
    void testFindByUsernameOrEmail() {
        VictorLogger.info("\n=== Common Test: Dynamic Query - findByUsernameOrEmail ===");

        String user1 = "or_test1_" + System.nanoTime();
        String user2 = "or_test2_" + System.nanoTime();

        userRepository.save(new UserDto(null, user1, user1 + "@example.com", 25, true, "Or Test 1"));
        userRepository.save(new UserDto(null, user2, user2 + "@example.com", 30, true, "Or Test 2"));

        List<UserDto> results = userRepository.findByUsernameOrEmail(user1, user2 + "@example.com");

        assertTrue(results.size() >= 2);
    }

    @Test
    @Order(11)
    @DisplayName("Common: Dynamic query - findByAgeLessThan")
    void testFindByAgeLessThan() {
        VictorLogger.info("\n=== Common Test: Dynamic Query - findByAgeLessThan ===");

        insertStandardTestData();

        List<UserDto> results = userRepository.findByAgeLessThan(25);

        assertFalse(results.isEmpty());
        results.forEach(user -> assertTrue(user.age() < 25));
    }

    @Test
    @Order(12)
    @DisplayName("Common: Dynamic query - findByNameLike")
    void testFindByNameLike() {
        VictorLogger.info("\n=== Common Test: Dynamic Query - findByNameLike ===");

        String username = "like_john_" + System.nanoTime();
        userRepository.save(new UserDto(null, username, username + "@example.com", 25, true, "John Doe Like"));

        List<UserDto> results = userRepository.findByNameLike("John");

        assertFalse(results.isEmpty());
        results.forEach(user -> assertTrue(user.name().toLowerCase().contains("john")));
    }

    @Test
    @Order(13)
    @DisplayName("Common: Dynamic query - findByEmailIsNull")
    void testFindByEmailIsNull() {
        VictorLogger.info("\n=== Common Test: Dynamic Query - findByEmailIsNull ===");

        String username = "null_email_" + System.nanoTime();
        userRepository.save(new UserDto(null, username, null, 25, true, "Null Email"));

        List<UserDto> results = userRepository.findByEmailIsNull();

        assertFalse(results.isEmpty());
        results.forEach(user -> assertNull(user.email()));
    }

    @Test
    @Order(14)
    @DisplayName("Common: Dynamic query - findByEmailIsNotNull")
    void testFindByEmailIsNotNull() {
        VictorLogger.info("\n=== Common Test: Dynamic Query - findByEmailIsNotNull ===");

        insertStandardTestData();

        List<UserDto> results = userRepository.findByEmailIsNotNull();

        assertFalse(results.isEmpty());
        results.forEach(user -> assertNotNull(user.email()));
    }

    @Test
    @Order(15)
    @DisplayName("Common: Dynamic query - findByActiveOrderByUsernameAsc")
    void testFindByActiveOrderByUsernameAsc() {
        VictorLogger.info("\n=== Common Test: Dynamic Query - findByActiveOrderByUsernameAsc ===");

        insertStandardTestData();

        List<UserDto> results = userRepository.findByActiveOrderByUsernameAsc(true);

        assertTrue(results.size() >= 3);

        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(
                results.get(i).username().compareTo(results.get(i + 1).username()) <= 0,
                "Results should be sorted by username ASC"
            );
        }
    }

    @Test
    @Order(16)
    @DisplayName("Common: Dynamic query - findByActiveOrderByUsernameDesc")
    void testFindByActiveOrderByUsernameDesc() {
        VictorLogger.info("\n=== Common Test: Dynamic Query - findByActiveOrderByUsernameDesc ===");

        insertStandardTestData();

        List<UserDto> results = userRepository.findByActiveOrderByUsernameDesc(true);

        assertTrue(results.size() >= 3);

        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(
                results.get(i).username().compareTo(results.get(i + 1).username()) >= 0,
                "Results should be sorted by username DESC"
            );
        }
    }

    @Test
    @Order(17)
    @DisplayName("Common: Dynamic query - complex query")
    void testComplexQuery() {
        VictorLogger.info("\n=== Common Test: Dynamic Query - Complex ===");

        insertStandardTestData();

        List<UserDto> results = userRepository.findByActiveAndAgeGreaterThanOrderByNameAsc(true, 25);

        assertTrue(results.size() >= 2);
        results.forEach(user -> {
            assertTrue(user.active());
            assertTrue(user.age() > 25);
        });

        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(
                results.get(i).name().compareTo(results.get(i + 1).name()) <= 0,
                "Results should be sorted by name ASC"
            );
        }
    }

    // ========================================
    // @Query Tests
    // ========================================

    @Test
    @Order(18)
    @DisplayName("Common: @Query with positional parameters")
    void testCustomQueryWithPositionalParameters() {
        VictorLogger.info("\n=== Common Test: @Query with Positional Parameters ===");

        userRepository.save(new UserDto(null, "alice_" + System.nanoTime(), "alice@test.com", 25, true, "Alice"));
        userRepository.save(new UserDto(null, "bob_" + System.nanoTime(), "bob@test.com", 30, true, "Bob"));
        userRepository.save(new UserDto(null, "charlie_" + System.nanoTime(), "charlie@test.com", 20, false, "Charlie"));

        List<UserDto> results = userRepository.findActiveUsersOlderThan(25, true);

        assertNotNull(results);
        assertFalse(results.isEmpty());
        results.forEach(user -> {
            assertTrue(user.age() > 25);
            assertTrue(user.active());
        });
    }

    @Test
    @Order(19)
    @DisplayName("Common: @Query with named parameters")
    void testCustomQueryWithNamedParameters() {
        VictorLogger.info("\n=== Common Test: @Query with Named Parameters ===");

        String username = "named_" + System.nanoTime();
        userRepository.save(new UserDto(null, username, username + "@test.com", 25, true, "Named"));

        Optional<UserDto> result = userRepository.findByUsernameCustom(username);

        assertTrue(result.isPresent());
        assertEquals(username, result.get().username());
    }

    @Test
    @Order(20)
    @DisplayName("Common: @Query with COUNT")
    void testCustomCountQuery() {
        VictorLogger.info("\n=== Common Test: @Query with COUNT ===");

        userRepository.save(new UserDto(null, "count1_" + System.nanoTime(), "count1@test.com", 25, true, "Count 1"));
        userRepository.save(new UserDto(null, "count2_" + System.nanoTime(), "count2@test.com", 30, false, "Count 2"));

        long activeCount = userRepository.countByActive(true);
        long inactiveCount = userRepository.countByActive(false);

        assertTrue(activeCount >= 1);
        assertTrue(inactiveCount >= 1);
    }

    @Test
    @Order(21)
    @DisplayName("Common: @Query with UPDATE")
    void testCustomUpdateQuery() {
        VictorLogger.info("\n=== Common Test: @Query with UPDATE ===");

        String username = "update_" + System.nanoTime();
        userRepository.save(new UserDto(null, username, username + "@test.com", 20, true, "Update Test"));

        int updatedRows = userRepository.updateActiveByAge(false, 25);

        assertTrue(updatedRows >= 1);

        Optional<UserDto> updated = userRepository.findByUsername(username);
        assertTrue(updated.isPresent());
        assertFalse(updated.get().active());
    }

    @Test
    @Order(22)
    @DisplayName("Common: @Query with DELETE")
    void testCustomDeleteQuery() {
        VictorLogger.info("\n=== Common Test: @Query with DELETE ===");

        String username = "delete_" + System.nanoTime();
        userRepository.save(new UserDto(null, username, username + "@test.com", 18, true, "Delete Test"));

        long countBefore = userRepository.count();
        int deletedRows = userRepository.deleteByAgeLessThan(19);

        assertTrue(deletedRows >= 1);
        long countAfter = userRepository.count();
        assertTrue(countAfter < countBefore);
    }

    // ========================================
    // Advanced Transaction Tests
    // ========================================

    @Test
    @Order(23)
    @DisplayName("Common: Transaction with return value")
    void testTransactionWithReturn() {
        VictorLogger.info("\n=== Common Test: Transaction with Return Value ===");

        UserDto savedUser = victor.transaction(() -> {
            String username = "tx_return_" + System.nanoTime();
            return createAndSaveUser(username);
        });

        assertNotNull(savedUser);
        assertNotNull(savedUser.id());

        Optional<UserDto> found = userRepository.findById(savedUser.id());
        assertTrue(found.isPresent());
    }

    @Test
    @Order(24)
    @DisplayName("Common: Manual transaction commit")
    void testManualTransaction() {
        VictorLogger.info("\n=== Common Test: Manual Transaction ===");

        var tx = victor.beginTransaction();
        try {
            String user1 = "manual1_" + System.nanoTime();
            String user2 = "manual2_" + System.nanoTime();

            createAndSaveUser(user1);
            createAndSaveUser(user2);

            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        } finally {
            tx.close();
        }
    }

    @Test
    @Order(25)
    @DisplayName("Common: Manual transaction rollback")
    void testManualTransactionRollback() {
        VictorLogger.info("\n=== Common Test: Manual Transaction Rollback ===");

        var tx = victor.beginTransaction();
        try {
            String username = "manual_rollback_" + System.nanoTime();
            createAndSaveUser(username);

            tx.rollback();
        } finally {
            tx.close();
        }
    }

    @Test
    @Order(26)
    @DisplayName("Common: Multiple operations in transaction")
    void testMultipleOperationsInTransaction() {
        VictorLogger.info("\n=== Common Test: Multiple Operations in Transaction ===");

        victor.transaction(() -> {
            String user1 = "multi1_" + System.nanoTime();
            String user2 = "multi2_" + System.nanoTime();

            UserDto saved1 = createAndSaveUser(user1);
            UserDto saved2 = createAndSaveUser(user2);

            UserDto updated = new UserDto(
                saved1.id(),
                saved1.username(),
                "updated@example.com",
                26,
                saved1.active(),
                saved1.name()
            );
            userRepository.save(updated);

            Optional<UserDto> found = userRepository.findByUsername(user2);
            assertTrue(found.isPresent());

            long count = userRepository.count();
            assertTrue(count >= 2);
        });
    }
}