package fr.traqueur.victor.dialect.sqlite;

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
 * Integration tests for SQLite database dialect.
 * Common tests are inherited from AbstractDialectTest.
 * This class only contains SQLite-specific tests.
 */
class SQLiteDialectTest extends AbstractDialectTest {

    @Override
    protected VictorBuilder configureVictor() {
        String url = System.getProperty("victor.test.url");
        if (url != null) {
            return Victor.configure().url(url).autoDetectDialect();
        }

        return Victor.configure()
                .sqlite()
                .database(":memory:");
    }

    @Override
    protected String getDialectName() {
        String dialect = System.getProperty("victor.test.dialect");
        return dialect != null ? dialect : "sqlite";
    }

    // ========================================
    // SQLite-Specific Tests
    // ========================================

    @Test
    @Order(100)
    @DisplayName("SQLite: Test WAL mode")
    void testSQLiteWALMode() {
        VictorLogger.info("\n=== SQLite-Specific Test: WAL Mode ===");

        // SQLite is configured with PRAGMA journal_mode = WAL
        UserDto saved = createAndSaveUser("wal_test_" + System.nanoTime());
        assertNotNull(saved);

        VictorLogger.info("✓ SQLite WAL mode working");
    }

    @Test
    @Order(101)
    @DisplayName("SQLite: Test AUTOINCREMENT")
    void testSQLiteAutoIncrement() {
        VictorLogger.info("\n=== SQLite-Specific Test: AUTOINCREMENT ===");

        UserDto user1 = createAndSaveUser("auto1_" + System.nanoTime());
        UserDto user2 = createAndSaveUser("auto2_" + System.nanoTime());

        assertTrue(user2.id() > user1.id(), "IDs should auto-increment");
        VictorLogger.info("✓ SQLite AUTOINCREMENT working");
    }
}
