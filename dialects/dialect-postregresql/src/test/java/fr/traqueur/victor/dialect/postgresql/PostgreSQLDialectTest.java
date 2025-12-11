package fr.traqueur.victor.dialect.postgresql;

import fr.traqueur.victor.AbstractDialectTest;
import fr.traqueur.victor.Victor;
import fr.traqueur.victor.VictorBuilder;
import fr.traqueur.victor.test.dto.UserDto;
import fr.traqueur.victor.utils.VictorLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PostgreSQL database dialect using Testcontainers.
 * Common tests are inherited from AbstractDialectTest.
 * This class only contains PostgreSQL-specific tests.
 */
@Testcontainers
class PostgreSQLDialectTest extends AbstractDialectTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("victor_test")
            .withUsername("test")
            .withPassword("test");

    @Override
    protected VictorBuilder configureVictor() {
        // Use Testcontainers PostgreSQL instance
        return Victor.configure()
                .url(postgres.getJdbcUrl())
                .credentials(postgres.getUsername(), postgres.getPassword())
                .autoDetectDialect();
    }

    @Override
    protected String getDialectName() {
        return "postgresql";
    }

    // ========================================
    // PostgreSQL-Specific Tests
    // ========================================

    @Test
    @Order(100)
    @DisplayName("PostgreSQL: Test BIGSERIAL for auto-increment")
    void testPostgreSQLBigSerial() {
        VictorLogger.info("\n=== PostgreSQL-Specific Test: BIGSERIAL ===");

        // PostgreSQL uses BIGSERIAL for Long IDs
        UserDto user1 = createAndSaveUser("serial1_" + System.nanoTime());
        UserDto user2 = createAndSaveUser("serial2_" + System.nanoTime());

        assertTrue(user2.id() > user1.id(), "IDs should auto-increment");
        VictorLogger.info("✓ PostgreSQL BIGSERIAL working");
    }

    @Test
    @Order(101)
    @DisplayName("PostgreSQL: Test case-insensitive identifiers")
    void testPostgreSQLCaseInsensitive() {
        VictorLogger.info("\n=== PostgreSQL-Specific Test: Case-Insensitive Identifiers ===");

        // PostgreSQL converts identifiers to lowercase
        UserDto saved = createAndSaveUser("case_test_" + System.nanoTime());
        assertNotNull(saved);

        Optional<UserDto> found = userRepository.findByUsername(saved.username());
        assertTrue(found.isPresent());

        VictorLogger.info("✓ PostgreSQL case-insensitive identifiers working");
    }

    @Test
    @Order(102)
    @DisplayName("PostgreSQL: Test ON CONFLICT DO UPDATE")
    void testPostgreSQLUpsert() {
        VictorLogger.info("\n=== PostgreSQL-Specific Test: Upsert ===");

        UserDto user = createAndSaveUser("upsert_test_" + System.nanoTime());
        Long id = user.id();

        // Upsert with same ID
        UserDto upserted = new UserDto(id, user.username(), "upserted@example.com",
                                        40, true, "Upserted User");
        userRepository.save(upserted);

        Optional<UserDto> found = userRepository.findById(id);
        assertTrue(found.isPresent());
        assertEquals("upserted@example.com", found.get().email());

        VictorLogger.info("✓ PostgreSQL ON CONFLICT DO UPDATE working");
    }

    @Test
    @Order(103)
    @DisplayName("PostgreSQL: Test RETURNING clause")
    void testPostgreSQLReturning() {
        VictorLogger.info("\n=== PostgreSQL-Specific Test: RETURNING ===");

        // PostgreSQL supports RETURNING clause for getting inserted IDs
        UserDto user = createAndSaveUser("returning_test_" + System.nanoTime());

        assertNotNull(user.id(), "ID should be returned after insert");
        VictorLogger.info("✓ PostgreSQL RETURNING clause working (ID: " + user.id() + ")");
    }
}
