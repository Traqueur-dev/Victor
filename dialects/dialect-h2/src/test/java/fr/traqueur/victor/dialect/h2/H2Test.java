package fr.traqueur.victor.dialect.h2;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.VictorBuilder;
import fr.traqueur.victor.core.AbstractTestRunner;
import fr.traqueur.victor.dto.UserDto;
import fr.traqueur.victor.utils.VictorLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class H2Test extends AbstractTestRunner {

    // ================================
    // CONFIGURATION UNIQUE DU DIALECT
    // ================================

    @Override
    protected VictorBuilder configureVictor() {
        return Victor.configure()
                .h2()
                .database("testdb_" + UUID.randomUUID().toString().replace("-", ""));
    }

    // ====================================================
    // TESTS SPECIFIQUES H2
    // ====================================================

    @Test
    @DisplayName("H2: MySQL compatibility mode")
    void testH2MySQLCompatibility() {
        VictorLogger.info("\n=== H2 Specific Test ===");

        var victor = configureVictor()
                .autoMigrate()
                .dtos(UserDto.class)
                .build();

        var repo = victor.createRepository(
                fr.traqueur.victor.repository.UserRepository.class);

        UserDto saved = repo.save(
                new UserDto(null,
                        "mysql_compat_" + System.nanoTime(),
                        "test@test.com",
                        25,
                        true,
                        "Test")
        );

        assertNotNull(saved.id());

        victor.close();
    }
}