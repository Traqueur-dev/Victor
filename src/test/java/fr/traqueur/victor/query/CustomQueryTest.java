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
class CustomQueryTest {
    
    private static Victor victor;
    private static UserRepository userRepository;
    
    @BeforeAll
    static void setup() {
        victor = Victor.configure()
            .h2()
            .database("customquery_test")
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
        
        userRepository.save(new UserDto(null, "alice", "alice@example.com", 25, true, "Alice"));
        userRepository.save(new UserDto(null, "bob", "bob@example.com", 30, true, "Bob"));
        userRepository.save(new UserDto(null, "charlie", "charlie@example.com", 20, false, "Charlie"));
        userRepository.save(new UserDto(null, "diana", "diana@example.com", 35, true, "Diana"));
        userRepository.save(new UserDto(null, "eve", "eve@example.com", 28, false, "Eve"));
        
        System.out.println("✓ Test data inserted\n");
    }
    
    @Test
    @Order(1)
    void testCustomQueryWithPositionalParameters() {
        System.out.println("\n--- Test 1: Custom Query with Positional Parameters ---");
        
        List<UserDto> results = userRepository.findActiveUsersOlderThan(25, true);
        
        assertNotNull(results);
        assertEquals(2, results.size()); // Bob (30) et Diana (35)
        
        results.forEach(user -> {
            assertTrue(user.age() > 25);
            assertTrue(user.active());
        });
        
        System.out.println("✓ Found " + results.size() + " active users older than 25");
    }
    
    @Test
    @Order(2)
    void testCustomQueryWithNamedParameters() {
        System.out.println("\n--- Test 2: Custom Query with Named Parameters ---");
        
        Optional<UserDto> result = userRepository.findByUsernameCustom("alice");
        
        assertTrue(result.isPresent());
        assertEquals("alice", result.get().username());
        assertEquals("alice@example.com", result.get().email());
        
        System.out.println("✓ Found user: " + result.get().username());
    }
    
    @Test
    @Order(3)
    void testCustomCountQuery() {
        System.out.println("\n--- Test 3: Custom COUNT Query ---");
        
        long activeCount = userRepository.countByActive(true);
        long inactiveCount = userRepository.countByActive(false);
        
        assertEquals(3, activeCount);  // alice, bob, diana
        assertEquals(2, inactiveCount); // charlie, eve
        
        System.out.println("✓ Active users: " + activeCount);
        System.out.println("✓ Inactive users: " + inactiveCount);
    }

    @Test
    @Order(4)
    void testCustomUpdateQuery() {
        System.out.println("\n--- Test 4: Custom UPDATE Query ---");

        // Désactiver les utilisateurs de moins de 25 ans (strictement)
        int updatedRows = userRepository.updateActiveByAge(false, 25);

        assertEquals(1, updatedRows);

        Optional<UserDto> charlie = userRepository.findByUsername("charlie");
        assertTrue(charlie.isPresent());
        assertFalse(charlie.get().active());

        Optional<UserDto> alice = userRepository.findByUsername("alice");
        assertTrue(alice.isPresent());
        assertTrue(alice.get().active());

        System.out.println("✓ Updated " + updatedRows + " user(s)");
    }
    
    @Test
    @Order(5)
    void testCustomDeleteQuery() {
        System.out.println("\n--- Test 5: Custom DELETE Query ---");
        
        long countBefore = userRepository.count();
        System.out.println("Users before delete: " + countBefore);
        
        // Supprimer les utilisateurs de moins de 25 ans
        int deletedRows = userRepository.deleteByAgeLessThan(25);
        
        assertEquals(1, deletedRows); // charlie (20)
        
        long countAfter = userRepository.count();
        assertEquals(countBefore - deletedRows, countAfter);
        
        System.out.println("✓ Deleted " + deletedRows + " users");
        System.out.println("Users after delete: " + countAfter);
    }
    
    @AfterAll
    static void tearDown() {
        if (victor != null) {
            victor.close();
            System.out.println("\n✓ Victor closed");
        }
    }
}