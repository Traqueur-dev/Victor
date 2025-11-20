package fr.traqueur.victor;

import fr.traqueur.victor.exceptions.VictorConfigurationException;
import fr.traqueur.victor.types.VictorDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

class VictorConfigurationTest {

    @AfterEach
    void cleanup() {
        try {
            var instance = Victor.getDefault();
            if (instance != null) {
                instance.close();
            }
        } catch (Exception ignored) {
            // Ignore si pas d'instance par défaut
        }
    }

    @Test
    void testSQLiteConfiguration() {
        var victor = Victor.sqlite("test.db");

        assertNotNull(victor);
        var config = victor.getConfiguration();
        assertEquals(VictorDialect.SQLITE, config.dialect());
        assertTrue(config.connectionUrl().contains("sqlite"));
        assertTrue(config.autoMigrate());
    }

    @Test
    void testBuilderConfiguration() {
        var victor = Victor.configure()
                .h2()
                .database("testdb")
                .showSql()
                .autoMigrate()
                .build();

        assertNotNull(victor);
        var config = victor.getConfiguration();
        assertEquals(VictorDialect.H2, config.dialect());
        assertTrue(config.showSql());
        assertTrue(config.autoMigrate());
    }

    @Test
    void testDialectDetection() {
        var victor = Victor.configure()
                .url("jdbc:sqlite:test.db")
                .autoDetectDialect()
                .build();

        var config = victor.getConfiguration();
        assertEquals(VictorDialect.SQLITE, config.dialect());
    }

    @Test
    void testInvalidConfiguration() {
        assertThrows(VictorConfigurationException.class, () -> {
            Victor.configure().build(); // No dialect specified
        });

        assertThrows(VictorConfigurationException.class, () -> {
            Victor.configure()
                    .mysql()
                    .build(); // No host specified for MySQL
        });
    }

    @Test
    void testStaticConnect() {
        Victor.connect("jdbc:h2:mem:test");

        var victor = Victor.getDefault();
        assertNotNull(victor);

        var config = victor.getConfiguration();
        assertEquals(VictorDialect.H2, config.dialect());
    }

    @Test
    void testConnectionProperties() {
        var victor = Victor.configure()
                .h2()
                .database("testdb")
                .property("DB_CLOSE_DELAY", "-1")
                .property("MODE", "PostgreSQL")
                .build();

        try {
            var config = victor.getConfiguration();
            var props = config.connectionProperties();

            assertEquals("-1", props.getProperty("DB_CLOSE_DELAY"));
            assertEquals("PostgreSQL", props.getProperty("MODE"));
        } finally {
            victor.close();
        }
    }
}