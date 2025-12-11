package fr.traqueur.victor.query;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.test.dto.UserDto;
import fr.traqueur.victor.test.entities.User;
import fr.traqueur.victor.test.repository.UserRepository;
import fr.traqueur.victor.utils.VictorLogger;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case tests for dynamic queries.
 * Common dynamic query tests are in AbstractDialectTest and run on all dialects.
 * This class contains only specific edge cases for H2.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DynamicQueryTest {

    private static Victor victor;
    private static UserRepository userRepository;

    @BeforeAll
    static void setup() {
        victor = Victor.configure()
            .h2()
            .database("testdb_dynamic_" + System.nanoTime())
            .showSql()
            .autoMigrate()
            .entities(User.class)
            .build();

        userRepository = victor.createRepository(UserRepository.class);
    }

    @Test
    @Order(1)
    @DisplayName("Edge case: findByUsername with non-existent user")
    void testFindByUsernameNotFound() {
        VictorLogger.info("\n--- Edge Case: findByUsername (not found) ---");

        Optional<UserDto> result = userRepository.findByUsername("unknown_user_" + System.nanoTime());

        assertFalse(result.isPresent(), "Should not find user");

        VictorLogger.info("✓ User not found (as expected)");
    }

    @AfterAll
    static void tearDown() {
        if (victor != null) {
            victor.close();
            VictorLogger.info("\n✓ Victor closed");
        }
    }
}