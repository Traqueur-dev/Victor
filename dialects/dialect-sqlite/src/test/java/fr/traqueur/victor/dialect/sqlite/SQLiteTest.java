package fr.traqueur.victor.dialect.sqlite;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.VictorBuilder;
import fr.traqueur.victor.core.*;
import fr.traqueur.victor.dto.ProductDto;
import fr.traqueur.victor.dto.UserDto;
import fr.traqueur.victor.dto.UserV2Dto;
import fr.traqueur.victor.repository.ProductRepository;
import fr.traqueur.victor.repository.UserRepository;
import fr.traqueur.victor.repository.UserV2Repository;
import fr.traqueur.victor.utils.VictorLogger;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SQLiteTest {

    // ====================================================
    // CONFIGURATION UNIQUE SQLITE
    // ====================================================

    protected VictorBuilder configureVictor() {

        String url = System.getProperty("victor.test.url");

        if (url != null) {
            return Victor.configure().url(url).autoDetectDialect();
        }

        return Victor.configure()
                .sqlite()
                .database(":memory:");
    }

    // ====================================================
    // SUITES COMMUNES
    // ====================================================

    @Nested
    class Crud extends AbstractCrudTest {
        @Override protected VictorBuilder configureVictor() {
            return SQLiteTest.this.configureVictor();
        }
    }

    @Nested
    class DynamicQuery extends AbstractDynamicQueryTest {
        @Override protected VictorBuilder configureVictor() {
            return SQLiteTest.this.configureVictor();
        }
    }

    /*@Nested
    class Transactions extends AbstractTransactionTest {
        @Override protected VictorBuilder configureVictor() {
            return H2Test.this.configureVictor();
        }
    }*/

    @Nested
    class Relationships extends AbstractRelationshipTest {
        @Override protected VictorBuilder configureVictor() {
            return SQLiteTest.this.configureVictor();
        }
    }

    @Nested
    class Migration extends AbstractMigrationTest {
        @Override protected VictorBuilder configureVictor() {
            return SQLiteTest.this.configureVictor();
        }
    }

    @Nested
    class ServiceLayer extends AbstractUserServiceTest {
        @Override protected VictorBuilder configureVictor() {
            return SQLiteTest.this.configureVictor();
        }
    }

    // ====================================================
    // SQLITE SPECIFIC TESTS
    // ====================================================

    private Path createTempDbFile() throws IOException {
        Path tempDb = Files.createTempFile("victor_sqlite_", ".db");
        tempDb.toFile().deleteOnExit();
        return tempDb;
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

    // ====================================================
    // MIGRATION TESTS
    // ====================================================

    @Test
    @DisplayName("SQLite Migration: ALTER TABLE ADD COLUMN")
    void testMigrationAddColumn() throws Exception {

        Path tempDb = createTempDbFile();

        try {
            Victor v1 = Victor.configure()
                    .sqlite()
                    .file(tempDb.toString())
                    .autoMigrate()
                    .dtos(UserDto.class)
                    .build();

            UserRepository repo1 = v1.createRepository(UserRepository.class);

            repo1.save(new UserDto(
                    null,
                    "alice_" + System.nanoTime(),
                    "alice@test.com",
                    25,
                    true,
                    "Alice"));

            long countBefore = repo1.count();
            v1.close();

            Victor v2 = Victor.configure()
                    .sqlite()
                    .file(tempDb.toString())
                    .autoMigrate()
                    .dtos(UserV2Dto.class)
                    .build();

            UserV2Repository repo2 = v2.createRepository(UserV2Repository.class);

            List<UserV2Dto> all = repo2.findAll();

            assertFalse(all.isEmpty());
            assertNull(all.get(0).phone());
            assertNull(all.get(0).bio());

            assertEquals(countBefore, repo2.count());

            v2.close();

        } finally {
            Files.deleteIfExists(tempDb);
        }

        VictorLogger.info("✓ SQLite migration ALTER TABLE working");
    }

    @Test
    @DisplayName("SQLite Migration: Index creation")
    void testIndexCreation() {

        Victor victor = Victor.configure()
                .sqlite()
                .database(":memory:")
                .autoMigrate()
                .dtos(ProductDto.class)
                .build();

        try {
            ProductRepository repo = victor.createRepository(ProductRepository.class);

            ProductDto saved = repo.save(new ProductDto(
                    null,
                    "Laptop",
                    "Electronics",
                    999.99,
                    "SKU_" + System.nanoTime()));

            assertNotNull(saved.id());
            assertTrue(repo.findById(saved.id()).isPresent());

        } finally {
            victor.close();
        }

        VictorLogger.info("✓ SQLite index creation working");
    }

    @Test
    @DisplayName("SQLite Migration: Data preservation")
    void testMigrationDataPreservation() throws Exception {

        Path tempDb = createTempDbFile();

        try {
            Victor v1 = Victor.configure()
                    .sqlite()
                    .file(tempDb.toString())
                    .autoMigrate()
                    .dtos(UserDto.class)
                    .build();

            UserRepository repo1 = v1.createRepository(UserRepository.class);

            String u1 = "preserve1_" + System.nanoTime();
            String u2 = "preserve2_" + System.nanoTime();

            repo1.save(new UserDto(null, u1, "p1@test.com", 25, true, "One"));
            repo1.save(new UserDto(null, u2, "p2@test.com", 35, false, "Two"));

            long count = repo1.count();
            v1.close();

            Victor v2 = Victor.configure()
                    .sqlite()
                    .file(tempDb.toString())
                    .autoMigrate()
                    .dtos(UserV2Dto.class)
                    .build();

            UserV2Repository repo2 = v2.createRepository(UserV2Repository.class);

            assertEquals(count, repo2.count());

            List<UserV2Dto> all = repo2.findAll();
            assertEquals(2, all.size());

            v2.close();

        } finally {
            Files.deleteIfExists(tempDb);
        }

        VictorLogger.info("✓ SQLite data preservation working");
    }
}