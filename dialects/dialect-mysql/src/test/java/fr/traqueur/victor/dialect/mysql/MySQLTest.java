package fr.traqueur.victor.dialect.mysql;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.VictorBuilder;
import fr.traqueur.victor.core.AbstractTestRunner;
import fr.traqueur.victor.dto.UserDto;
import fr.traqueur.victor.repository.UserRepository;
import fr.traqueur.victor.utils.VictorLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class MySQLTest extends AbstractTestRunner {

    // ====================================================
    // TESTCONTAINER MYSQL
    // ====================================================

    @Container
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("victor_test")
                    .withUsername("test")
                    .withPassword("test");

    // ====================================================
    // CONFIGURATION UNIQUE
    // ====================================================

    @Override
    protected VictorBuilder configureVictor() {
        return Victor.configure()
                .url(mysql.getJdbcUrl())
                .credentials(mysql.getUsername(), mysql.getPassword())
                .autoDetectDialect();
    }

    // ====================================================
    // TESTS SPECIFIQUES MYSQL
    // ====================================================

    @Test
    @DisplayName("MySQL: Test InnoDB engine")
    void testMySQLInnoDBEngine() {
        VictorLogger.info("\n=== MySQL-Specific Test: InnoDB Engine ===");
        Victor victor = configureVictor()
                .autoMigrate()
                .dtos(UserDto.class)
                .build();
        UserRepository repo = victor.createRepository(UserRepository.class);

        UserDto saved = repo.save(
                new UserDto(null,
                        "innodb_test_" + System.nanoTime(),
                        "test@test.com",
                        25,
                        true,
                        "Test")
        );

        assertNotNull(saved.id());
        victor.close();

        VictorLogger.info("✓ MySQL InnoDB engine working");
    }

    @Test
    @DisplayName("MySQL: Test UTF8MB4 charset (emoji support)")
    void testMySQLUTF8MB4() {
        VictorLogger.info("\n=== MySQL-Specific Test: UTF8MB4 Charset ===");

        String username = "emoji_test_😀_" + System.nanoTime();
        Victor victor = configureVictor()
                .autoMigrate()
                .dtos(UserDto.class)
                .build();
        UserRepository repo = victor.createRepository(UserRepository.class);

        UserDto saved = repo.save(
                new UserDto(null, username, "emoji@test.com", 25, true, "Emoji")
        );

        assertNotNull(saved.id());

        Optional<UserDto> found = repo.findByUsername(username);
        assertTrue(found.isPresent());
        victor.close();

        VictorLogger.info("✓ MySQL UTF8MB4 charset working");
    }

    @Test
    @DisplayName("MySQL: Test ON DUPLICATE KEY UPDATE (Upsert)")
    void testMySQLUpsert() {
        VictorLogger.info("\n=== MySQL-Specific Test: Upsert ===");
        Victor victor = configureVictor()
                .autoMigrate()
                .dtos(UserDto.class)
                .build();
        UserRepository repo = victor.createRepository(UserRepository.class);

        UserDto user = repo.save(
                new UserDto(null,
                        "upsert_test_" + System.nanoTime(),
                        "test@test.com",
                        25,
                        true,
                        "Original")
        );

        Long id = user.id();

        UserDto updated = new UserDto(
                id,
                user.username(),
                "upserted@example.com",
                40,
                true,
                "Updated"
        );

        repo.save(updated);

        Optional<UserDto> found = repo.findById(id);

        assertTrue(found.isPresent());
        assertEquals("upserted@example.com", found.get().email());
        victor.close();

        VictorLogger.info("✓ MySQL ON DUPLICATE KEY UPDATE working");
    }
}