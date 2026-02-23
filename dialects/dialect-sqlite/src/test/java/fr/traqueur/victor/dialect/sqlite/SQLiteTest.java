package fr.traqueur.victor.dialect.sqlite;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.VictorBuilder;
import fr.traqueur.victor.core.AbstractMigrationTest;
import fr.traqueur.victor.core.AbstractTestRunner;
import fr.traqueur.victor.dto.ProductDto;
import fr.traqueur.victor.dto.UserDto;
import fr.traqueur.victor.dto.UserV2Dto;
import fr.traqueur.victor.repository.ProductRepository;
import fr.traqueur.victor.repository.UserRepository;
import fr.traqueur.victor.repository.UserV2Repository;
import fr.traqueur.victor.utils.VictorLogger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SQLiteTest extends AbstractTestRunner {

    // ====================================================
    // CONFIGURATION UNIQUE SQLITE
    // ====================================================

    @Override
    protected VictorBuilder configureVictor() {
        String dbName = "memdb_" + UUID.randomUUID().toString().replace("-", "");
        return Victor.configure()
                .sqlite()
                .database("file:" + dbName + "?mode=memory&cache=shared");
    }

    @Nested
    class Migration extends AbstractMigrationTest {

        @TempDir
        Path tempDir;

        @Override
        protected VictorBuilder configureVictor() {
            try {
                Path db = tempDir.resolve("migration.db");
                return Victor.configure()
                        .sqlite()
                        .file(db.toString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    @DisplayName("SQLite: WAL mode + AUTOINCREMENT")
    void testSQLiteBasics() {

        Victor victor = configureVictor()
                .showSql()
                .autoMigrate()
                .dtos(UserDto.class)
                .build();

        try {
            UserRepository repo = victor.createRepository(UserRepository.class);

            UserDto u1 = repo.save(new UserDto(
                    null,
                    "auto1_" + System.nanoTime(),
                    "a@test.com",
                    25,
                    true,
                    "A"));

            UserDto u2 = repo.save(new UserDto(
                    null,
                    "auto2_" + System.nanoTime(),
                    "b@test.com",
                    25,
                    true,
                    "B"));

            assertTrue(u2.id() > u1.id());
        } finally {
            victor.close();
        }

        VictorLogger.info("✓ SQLite basics working");
    }

}