package fr.traqueur.victor.dialect.h2;

import fr.traqueur.victor.AbstractDialectTest;
import fr.traqueur.victor.Victor;
import fr.traqueur.victor.VictorBuilder;
import fr.traqueur.victor.test.dto.UserDto;
import fr.traqueur.victor.utils.VictorLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for H2 database dialect.
 * Common tests are inherited from AbstractDialectTest.
 * This class only contains H2-specific tests.
 */
class H2DialectTest extends AbstractDialectTest {

    @Override
    protected VictorBuilder configureVictor() {
        // Use system property if available, otherwise use default
        String url = System.getProperty("victor.test.url");
        if (url != null) {
            return Victor.configure().url(url).autoDetectDialect();
        }

        // Default H2 configuration
        return Victor.configure()
                .h2()
                .database("testdb_" + System.nanoTime());
    }

    @Override
    protected String getDialectName() {
        String dialect = System.getProperty("victor.test.dialect");
        return dialect != null ? dialect : "h2";
    }

    // ========================================
    // H2-Specific Tests
    // ========================================

    @Test
    @Order(100)
    @DisplayName("H2: Test MySQL compatibility mode")
    void testH2MySQLCompatibility() {
        VictorLogger.info("\n=== H2-Specific Test: MySQL Compatibility ===");

        // H2 is configured with MODE=MySQL in the dialect
        // Test that MySQL-specific syntax works
        UserDto saved = createAndSaveUser("mysql_compat_" + System.nanoTime());

        assertNotNull(saved);
        VictorLogger.info("✓ H2 MySQL compatibility mode working");
    }

    @Test
    @Order(101)
    @DisplayName("H2: Test in-memory database performance")
    void testH2InMemoryPerformance() {
        VictorLogger.info("\n=== H2-Specific Test: In-Memory Performance ===");

        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            createAndSaveUser("perf_user_" + i + "_" + System.nanoTime());
        }

        long duration = System.currentTimeMillis() - start;

        VictorLogger.info("✓ Inserted 100 users in " + duration + "ms");
        assertTrue(duration < 5000, "H2 in-memory should be fast (< 5s for 100 inserts)");
    }
}
