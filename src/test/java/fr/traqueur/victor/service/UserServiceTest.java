package fr.traqueur.victor.service;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.test.dto.UserDto;
import fr.traqueur.victor.test.entities.User;
import fr.traqueur.victor.test.repository.UserRepository;
import fr.traqueur.victor.test.service.UserService;
import fr.traqueur.victor.utils.VictorLogger;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceTest {

    private static Victor victor;
    private static UserService userService;

    @BeforeAll
    static void setup() {
        // Créer Victor avec H2 en mémoire
        victor = Victor.configure()
            .h2()
            .database("testdb_service")
            .showSql()
            .autoMigrate()
            .entities(User.class)
            .build();

        userService = victor.createService(UserService.class);
    }

    @AfterAll
    static void tearDown() {
        if (victor != null) {
            victor.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Save a user via service")
    void testSaveUser() {
        VictorLogger.info("\n=== Test 1: Save user ===");

        User user = new User("john_doe", "john@example.com", 25, true, "John Doe");
        User saved = userService.save(user);

        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("john_doe", saved.getUsername());
        assertEquals("john@example.com", saved.getEmail());
        assertEquals(25, saved.getAge());
        assertTrue(saved.getActive());
        assertEquals("John Doe", saved.getName());

        VictorLogger.info("Saved user: " + saved);
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Find user by ID")
    void testFindById() {
        VictorLogger.info("\n=== Test 2: Find by ID ===");

        // Save a user first
        User user = new User("jane_smith", "jane@example.com", 30, true, "Jane Smith");
        User saved = userService.save(user);

        // Find by ID
        Optional<User> found = userService.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals("jane_smith", found.get().getUsername());
        assertEquals("jane@example.com", found.get().getEmail());

        VictorLogger.info("Found user: " + found.get());
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Find all users")
    void testFindAll() {
        VictorLogger.info("\n=== Test 3: Find all ===");

        // Save multiple users
        userService.save(new User("user1", "user1@test.com", 20, true, "User One"));
        userService.save(new User("user2", "user2@test.com", 25, true, "User Two"));

        List<User> users = userService.findAll();

        assertNotNull(users);
        assertTrue(users.size() >= 2);

        VictorLogger.info("Total users: " + users.size());
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Update user")
    void testUpdateUser() {
        VictorLogger.info("\n=== Test 4: Update user ===");

        // Save a user
        User user = new User("bob_wilson", "bob@example.com", 20, false, "Bob Wilson");
        User saved = userService.save(user);

        // Update the user
        saved.setAge(21);
        saved.setActive(true);
        User updated = userService.update(saved.getId(), saved);

        assertEquals(21, updated.getAge());
        assertTrue(updated.getActive());

        VictorLogger.info("Updated user: " + updated);
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Delete user by ID")
    void testDeleteById() {
        VictorLogger.info("\n=== Test 5: Delete by ID ===");

        // Save a user
        User user = new User("to_delete", "delete@example.com", 30, true, "To Delete");
        User saved = userService.save(user);
        Long userId = saved.getId();

        // Verify user exists
        assertTrue(userService.exists(userId));

        // Delete user
        userService.deleteById(userId);

        // Verify user is deleted
        assertFalse(userService.exists(userId));

        VictorLogger.info("User deleted successfully");
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Delete user by entity")
    void testDeleteByEntity() {
        VictorLogger.info("\n=== Test 6: Delete by entity ===");

        // Save a user
        User user = new User("to_delete2", "delete2@example.com", 35, true, "To Delete 2");
        User saved = userService.save(user);

        // Delete user
        userService.delete(saved);

        // Verify user is deleted
        assertFalse(userService.exists(saved.getId()));

        VictorLogger.info("User deleted successfully");
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Check if user exists")
    void testExists() {
        VictorLogger.info("\n=== Test 7: Exists ===");

        // Save a user
        User user = new User("exists_user", "exists@example.com", 28, true, "Exists User");
        User saved = userService.save(user);

        // Check exists
        assertTrue(userService.exists(saved.getId()));
        assertFalse(userService.exists(99999L));

        VictorLogger.info("Exists check passed");
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Count users")
    void testCount() {
        VictorLogger.info("\n=== Test 8: Count ===");

        long count = userService.count();

        assertTrue(count > 0);

        VictorLogger.info("Total count: " + count);
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: Save all users")
    void testSaveAll() {
        VictorLogger.info("\n=== Test 9: Save all ===");

        List<User> users = List.of(
            new User("bulk1", "bulk1@test.com", 25, true, "Bulk User 1"),
            new User("bulk2", "bulk2@test.com", 30, true, "Bulk User 2"),
            new User("bulk3", "bulk3@test.com", 35, true, "Bulk User 3")
        );

        List<User> saved = userService.saveAll(users);

        assertNotNull(saved);
        assertEquals(3, saved.size());

        // Verify all have IDs
        saved.forEach(user -> assertNotNull(user.getId()));

        VictorLogger.info("Saved " + saved.size() + " users in bulk");
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Delete all by IDs")
    void testDeleteAll() {
        VictorLogger.info("\n=== Test 10: Delete all ===");

        // Save users
        List<User> users = List.of(
            new User("del1", "del1@test.com", 25, true, "Del 1"),
            new User("del2", "del2@test.com", 30, true, "Del 2")
        );
        List<User> saved = userService.saveAll(users);
        List<Long> ids = saved.stream().map(User::getId).toList();

        // Delete all
        userService.deleteAll(ids);

        // Verify deleted
        ids.forEach(id -> assertFalse(userService.exists(id)));

        VictorLogger.info("Deleted all users");
    }

    @Test
    @Order(11)
    @DisplayName("Test 11: Validate and save - valid user")
    void testValidateAndSaveValid() {
        VictorLogger.info("\n=== Test 11: Validate and save (valid) ===");

        User user = new User("valid_user", "valid@example.com", 28, true, "Valid User");

        User saved = userService.validateAndSave(user);

        assertNotNull(saved);
        assertNotNull(saved.getId());

        VictorLogger.info("Valid user saved: " + saved);
    }

    @Test
    @Order(12)
    @DisplayName("Test 12: Access repository through service")
    void testAccessRepository() {
        VictorLogger.info("\n=== Test 12: Access repository ===");

        // Accéder au repository via le service
        UserRepository repository = userService.repository();

        assertNotNull(repository);

        // Utiliser une méthode spécifique du repository
        User user = new User("repo_test", "repo@test.com", 27, true, "Repository Test");
        User saved = userService.save(user);

        // Utiliser une méthode personnalisée du repository
        Optional<UserDto> found = repository.findByUsername("repo_test");

        assertTrue(found.isPresent());
        assertEquals("repo_test", found.get().username());

        VictorLogger.info("Repository access successful: " + found.get());
    }

    @Test
    @Order(13)
    @DisplayName("Test 13: Use custom repository methods via service")
    void testCustomRepositoryMethods() {
        VictorLogger.info("\n=== Test 13: Custom repository methods ===");

        // Sauvegarder des utilisateurs de test
        userService.save(new User("alice", "alice@test.com", 22, true, "Alice Wonder"));
        userService.save(new User("bob_custom", "bob@test.com", 35, false, "Bob Custom"));
        userService.save(new User("charlie", "charlie@test.com", 28, true, "Charlie Brown"));

        UserRepository repo = userService.repository();

        // Test findByUsername
        Optional<UserDto> alice = repo.findByUsername("alice");
        assertTrue(alice.isPresent());
        assertEquals("Alice Wonder", alice.get().name());

        // Test findByAgeGreaterThan
        List<UserDto> olderUsers = repo.findByAgeGreaterThan(30);
        assertFalse(olderUsers.isEmpty());

        // Test findByActiveOrderByUsernameAsc
        List<UserDto> activeUsers = repo.findByActiveOrderByUsernameAsc(true);
        assertFalse(activeUsers.isEmpty());

        VictorLogger.info("Custom repository methods work correctly");
    }

    @Test
    @Order(14)
    @DisplayName("Test 14: Use @Query methods via service")
    void testQueryMethods() {
        VictorLogger.info("\n=== Test 14: @Query methods ===");

        // Sauvegarder des utilisateurs de test
        userService.save(new User("query1", "query1@test.com", 40, true, "Query Test 1"));
        userService.save(new User("query2", "query2@test.com", 18, false, "Query Test 2"));

        UserRepository repo = userService.repository();

        // Test custom query
        List<UserDto> activeOlder = repo.findActiveUsersOlderThan(30, true);
        assertNotNull(activeOlder);

        // Test named parameter query
        Optional<UserDto> query1 = repo.findByUsernameCustom("query1");
        assertTrue(query1.isPresent());
        assertEquals("query1", query1.get().username());

        // Test count query
        long activeCount = repo.countByActive(true);
        assertTrue(activeCount > 0);

        VictorLogger.info("@Query methods work correctly. Active count: " + activeCount);
    }
}