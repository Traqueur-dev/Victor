package fr.traqueur.victor.query;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.test.dto.UserDto;
import fr.traqueur.victor.test.entities.User;
import fr.traqueur.victor.test.repository.UserRepository;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DynamicQueryTest {
    
    private static Victor victor;
    private static UserRepository userRepository;
    
    @BeforeAll
    static void setup() {
        // Créer Victor avec H2 en mémoire
        victor = Victor.configure()
            .h2()
            .database("testdb")
            .showSql()
            .autoMigrate()
            .entities(User.class)
            .build();
        
        userRepository = victor.createRepository(UserRepository.class);
        
        // Insérer des données de test
        insertTestData();
    }
    
    private static void insertTestData() {
        System.out.println("\n=== Inserting test data ===");
        
        // User 1
        UserDto user1 = new UserDto(null, "john_doe", "john@example.com", 25, true, "John Doe");
        userRepository.save(user1);
        
        // User 2
        UserDto user2 = new UserDto(null, "jane_smith", "jane@example.com", 30, true, "Jane Smith");
        userRepository.save(user2);
        
        // User 3
        UserDto user3 = new UserDto(null, "bob_wilson", "bob@example.com", 20, false, "Bob Wilson");
        userRepository.save(user3);
        
        // User 4
        UserDto user4 = new UserDto(null, "alice_brown", null, 35, true, "Alice Brown");
        userRepository.save(user4);
        
        // User 5
        UserDto user5 = new UserDto(null, "charlie_jones", "charlie@example.com", 28, true, "Charlie Jones");
        userRepository.save(user5);
        
        System.out.println("✓ Test data inserted\n");
    }
    
    @Test
    @Order(1)
    void testFindByUsername() {
        System.out.println("\n--- Test 1: findByUsername ---");
        
        Optional<UserDto> result = userRepository.findByUsername("john_doe");
        
        assertTrue(result.isPresent(), "Should find user");
        assertEquals("john_doe", result.get().username());
        assertEquals("john@example.com", result.get().email());
        
        System.out.println("✓ Found: " + result.get());
    }
    
    @Test
    @Order(2)
    void testFindByUsernameNotFound() {
        System.out.println("\n--- Test 2: findByUsername (not found) ---");
        
        Optional<UserDto> result = userRepository.findByUsername("unknown_user");
        
        assertFalse(result.isPresent(), "Should not find user");
        
        System.out.println("✓ User not found (as expected)");
    }
    
    @Test
    @Order(3)
    void testFindByUsernameAndEmail() {
        System.out.println("\n--- Test 3: findByUsernameAndEmail ---");
        
        Optional<UserDto> result = userRepository.findByUsernameAndEmail("john_doe", "john@example.com");
        
        assertTrue(result.isPresent(), "Should find user");
        assertEquals("john_doe", result.get().username());
        
        System.out.println("✓ Found: " + result.get());
    }
    
    @Test
    @Order(4)
    void testFindByUsernameOrEmail() {
        System.out.println("\n--- Test 4: findByUsernameOrEmail ---");
        
        List<UserDto> results = userRepository.findByUsernameOrEmail("john_doe", "jane@example.com");
        
        assertEquals(2, results.size(), "Should find 2 users");
        
        System.out.println("✓ Found " + results.size() + " users:");
        results.forEach(u -> System.out.println("  - " + u.username()));
    }
    
    @Test
    @Order(5)
    void testFindByAgeGreaterThan() {
        System.out.println("\n--- Test 5: findByAgeGreaterThan ---");
        
        List<UserDto> results = userRepository.findByAgeGreaterThan(25);
        
        assertTrue(results.size() >= 3, "Should find at least 3 users with age > 25");
        results.forEach(user -> assertTrue(user.age() > 25, "Age should be > 25"));
        
        System.out.println("✓ Found " + results.size() + " users with age > 25:");
        results.forEach(u -> System.out.println("  - " + u.username() + " (age: " + u.age() + ")"));
    }
    
    @Test
    @Order(6)
    void testFindByAgeLessThan() {
        System.out.println("\n--- Test 6: findByAgeLessThan ---");
        
        List<UserDto> results = userRepository.findByAgeLessThan(25);

        assertFalse(results.isEmpty(), "Should find at least 1 user with age < 25");
        results.forEach(user -> assertTrue(user.age() < 25, "Age should be < 25"));
        
        System.out.println("✓ Found " + results.size() + " users with age < 25:");
        results.forEach(u -> System.out.println("  - " + u.username() + " (age: " + u.age() + ")"));
    }
    
    @Test
    @Order(7)
    void testFindByNameLike() {
        System.out.println("\n--- Test 7: findByNameLike ---");
        
        List<UserDto> results = userRepository.findByNameLike("John");

        assertFalse(results.isEmpty(), "Should find users with 'John' in name");
        results.forEach(user -> assertTrue(
            user.name().toLowerCase().contains("john"),
            "Name should contain 'John'"
        ));
        
        System.out.println("✓ Found " + results.size() + " users with 'John' in name:");
        results.forEach(u -> System.out.println("  - " + u.name()));
    }
    
    @Test
    @Order(8)
    void testFindByEmailIsNull() {
        System.out.println("\n--- Test 8: findByEmailIsNull ---");
        
        List<UserDto> results = userRepository.findByEmailIsNull();

        assertFalse(results.isEmpty(), "Should find users with null email");
        results.forEach(user -> assertNull(user.email(), "Email should be null"));
        
        System.out.println("✓ Found " + results.size() + " users with null email:");
        results.forEach(u -> System.out.println("  - " + u.username()));
    }
    
    @Test
    @Order(9)
    void testFindByEmailIsNotNull() {
        System.out.println("\n--- Test 9: findByEmailIsNotNull ---");
        
        List<UserDto> results = userRepository.findByEmailIsNotNull();
        
        assertTrue(results.size() >= 4, "Should find users with non-null email");
        results.forEach(user -> assertNotNull(user.email(), "Email should not be null"));
        
        System.out.println("✓ Found " + results.size() + " users with email:");
        results.forEach(u -> System.out.println("  - " + u.username() + " (" + u.email() + ")"));
    }
    
    @Test
    @Order(10)
    void testFindByActiveOrderByUsernameAsc() {
        System.out.println("\n--- Test 10: findByActiveOrderByUsernameAsc ---");
        
        List<UserDto> results = userRepository.findByActiveOrderByUsernameAsc(true);
        
        assertTrue(results.size() >= 3, "Should find active users");
        
        // Vérifier l'ordre
        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(
                results.get(i).username().compareTo(results.get(i + 1).username()) <= 0,
                "Results should be sorted by username ASC"
            );
        }
        
        System.out.println("✓ Found " + results.size() + " active users (sorted by username ASC):");
        results.forEach(u -> System.out.println("  - " + u.username()));
    }
    
    @Test
    @Order(11)
    void testFindByActiveOrderByUsernameDesc() {
        System.out.println("\n--- Test 11: findByActiveOrderByUsernameDesc ---");
        
        List<UserDto> results = userRepository.findByActiveOrderByUsernameDesc(true);
        
        assertTrue(results.size() >= 3, "Should find active users");
        
        // Vérifier l'ordre décroissant
        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(
                results.get(i).username().compareTo(results.get(i + 1).username()) >= 0,
                "Results should be sorted by username DESC"
            );
        }
        
        System.out.println("✓ Found " + results.size() + " active users (sorted by username DESC):");
        results.forEach(u -> System.out.println("  - " + u.username()));
    }
    
    @Test
    @Order(12)
    void testComplexQuery() {
        System.out.println("\n--- Test 12: findByActiveAndAgeGreaterThanOrderByNameAsc ---");
        
        List<UserDto> results = userRepository.findByActiveAndAgeGreaterThanOrderByNameAsc(true, 25);
        
        assertTrue(results.size() >= 2, "Should find active users with age > 25");
        results.forEach(user -> {
            assertTrue(user.active(), "User should be active");
            assertTrue(user.age() > 25, "Age should be > 25");
        });
        
        // Vérifier l'ordre par nom
        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(
                results.get(i).name().compareTo(results.get(i + 1).name()) <= 0,
                "Results should be sorted by name ASC"
            );
        }
        
        System.out.println("✓ Found " + results.size() + " active users with age > 25 (sorted by name):");
        results.forEach(u -> System.out.println("  - " + u.name() + " (age: " + u.age() + ")"));
    }
    
    @AfterAll
    static void tearDown() {
        if (victor != null) {
            victor.close();
            System.out.println("\n✓ Victor closed");
        }
    }
}