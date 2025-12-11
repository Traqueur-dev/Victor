package fr.traqueur.victor.dialect.mysql;

import fr.traqueur.victor.AbstractDialectTest;
import fr.traqueur.victor.Victor;
import fr.traqueur.victor.VictorBuilder;
import fr.traqueur.victor.test.dto.UserDto;
import fr.traqueur.victor.utils.VictorLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MySQL database dialect using Testcontainers.
 * Common tests are inherited from AbstractDialectTest.
 * This class only contains MySQL-specific tests.
 */
@Testcontainers
class MySQLDialectTest extends AbstractDialectTest {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("victor_test")
            .withUsername("test")
            .withPassword("test");

    @Override
    protected VictorBuilder configureVictor() {
        // Use Testcontainers MySQL instance
        return Victor.configure()
                .url(mysql.getJdbcUrl())
                .credentials(mysql.getUsername(), mysql.getPassword())
                .autoDetectDialect();
    }

    @Override
    protected String getDialectName() {
        return "mysql";
    }

    // ========================================
    // MySQL-Specific Tests
    // ========================================

    @Test
    @Order(100)
    @DisplayName("MySQL: Test InnoDB engine")
    void testMySQLInnoDBEngine() {
        VictorLogger.info("\n=== MySQL-Specific Test: InnoDB Engine ===");

        // Tables are created with ENGINE=InnoDB
        UserDto saved = createAndSaveUser("innodb_test_" + System.nanoTime());
        assertNotNull(saved);

        VictorLogger.info("✓ MySQL InnoDB engine working");
    }

    @Test
    @Order(101)
    @DisplayName("MySQL: Test UTF8MB4 charset")
    void testMySQLUTF8MB4() {
        VictorLogger.info("\n=== MySQL-Specific Test: UTF8MB4 Charset ===");

        // Test emoji support (UTF8MB4)
        String username = "emoji_test_😀_" + System.nanoTime();
        UserDto saved = createAndSaveUser(username);
        assertNotNull(saved);

        Optional<UserDto> found = userRepository.findByUsername(username);
        assertTrue(found.isPresent());

        VictorLogger.info("✓ MySQL UTF8MB4 charset working (emoji support)");
    }

    @Test
    @Order(102)
    @DisplayName("MySQL: Test ON DUPLICATE KEY UPDATE")
    void testMySQLUpsert() {
        VictorLogger.info("\n=== MySQL-Specific Test: Upsert ===");

        UserDto user = createAndSaveUser("upsert_test_" + System.nanoTime());
        Long id = user.id();

        // Upsert with same ID
        UserDto upserted = new UserDto(id, user.username(), "upserted@example.com",
                                        40, true, "Upserted User");
        userRepository.save(upserted);

        Optional<UserDto> found = userRepository.findById(id);
        assertTrue(found.isPresent());
        assertEquals("upserted@example.com", found.get().email());

        VictorLogger.info("✓ MySQL ON DUPLICATE KEY UPDATE working");
    }
}
