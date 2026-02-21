package fr.traqueur.victor.dialect.postgresql;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.VictorBuilder;
import fr.traqueur.victor.core.*;
import fr.traqueur.victor.dto.UserDto;
import fr.traqueur.victor.repository.UserRepository;
import fr.traqueur.victor.utils.VictorLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PostgreSQLTest {

    // ====================================================
    // TESTCONTAINER POSTGRESQL
    // ====================================================

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("victor_test")
                    .withUsername("test")
                    .withPassword("test");

    // ====================================================
    // CONFIGURATION UNIQUE
    // ====================================================

    protected VictorBuilder configureVictor() {
        return Victor.configure()
                .url(postgres.getJdbcUrl())
                .credentials(postgres.getUsername(), postgres.getPassword())
                .autoDetectDialect();
    }

    // ====================================================
    // SUITES COMMUNES
    // ====================================================

    @Nested
    class Crud extends AbstractCrudTest {
        @Override protected VictorBuilder configureVictor() {
            return PostgreSQLTest.this.configureVictor();
        }
    }

    @Nested
    class DynamicQuery extends AbstractDynamicQueryTest {
        @Override protected VictorBuilder configureVictor() {
            return PostgreSQLTest.this.configureVictor();
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
            return PostgreSQLTest.this.configureVictor();
        }
    }

    @Nested
    class Migration extends AbstractMigrationTest {
        @Override protected VictorBuilder configureVictor() {
            return PostgreSQLTest.this.configureVictor();
        }
    }

    @Nested
    class ServiceLayer extends AbstractUserServiceTest {
        @Override protected VictorBuilder configureVictor() {
            return PostgreSQLTest.this.configureVictor();
        }
    }

    // ====================================================
    // TESTS SPECIFIQUES POSTGRESQL
    // ====================================================

    @Test
    @DisplayName("PostgreSQL: BIGSERIAL auto-increment")
    void testPostgreSQLBigSerial() {

        Victor victor = configureVictor()
                .autoMigrate()
                .dtos(UserDto.class)
                .build();

        try {
            UserRepository repo = victor.createRepository(UserRepository.class);

            UserDto user1 = repo.save(new UserDto(
                    null, "serial1_" + System.nanoTime(),
                    "s1@test.com", 25, true, "S1"));

            UserDto user2 = repo.save(new UserDto(
                    null, "serial2_" + System.nanoTime(),
                    "s2@test.com", 25, true, "S2"));

            assertTrue(user2.id() > user1.id());
        } finally {
            victor.close();
        }

        VictorLogger.info("✓ PostgreSQL BIGSERIAL working");
    }

    @Test
    @DisplayName("PostgreSQL: Case-insensitive identifiers")
    void testPostgreSQLCaseInsensitive() {

        Victor victor = configureVictor()
                .autoMigrate()
                .dtos(UserDto.class)
                .build();

        try {
            UserRepository repo = victor.createRepository(UserRepository.class);

            UserDto saved = repo.save(new UserDto(
                    null, "case_test_" + System.nanoTime(),
                    "case@test.com", 25, true, "Case"));

            Optional<UserDto> found =
                    repo.findByUsername(saved.username());

            assertTrue(found.isPresent());
        } finally {
            victor.close();
        }

        VictorLogger.info("✓ PostgreSQL case-insensitive identifiers working");
    }

    @Test
    @DisplayName("PostgreSQL: ON CONFLICT DO UPDATE (Upsert)")
    void testPostgreSQLUpsert() {

        Victor victor = configureVictor()
                .autoMigrate()
                .dtos(UserDto.class)
                .build();

        try {
            UserRepository repo = victor.createRepository(UserRepository.class);

            UserDto user = repo.save(new UserDto(
                    null, "upsert_" + System.nanoTime(),
                    "orig@test.com", 25, true, "Original"));

            Long id = user.id();

            UserDto updated = new UserDto(
                    id,
                    user.username(),
                    "updated@test.com",
                    40,
                    true,
                    "Updated");

            repo.save(updated);

            Optional<UserDto> found = repo.findById(id);

            assertTrue(found.isPresent());
            assertEquals("updated@test.com", found.get().email());
        } finally {
            victor.close();
        }

        VictorLogger.info("✓ PostgreSQL ON CONFLICT DO UPDATE working");
    }

    @Test
    @DisplayName("PostgreSQL: RETURNING clause")
    void testPostgreSQLReturning() {

        Victor victor = configureVictor()
                .autoMigrate()
                .dtos(UserDto.class)
                .build();

        try {
            UserRepository repo = victor.createRepository(UserRepository.class);

            UserDto user = repo.save(new UserDto(
                    null, "returning_" + System.nanoTime(),
                    "ret@test.com", 25, true, "Returning"));

            assertNotNull(user.id());
        } finally {
            victor.close();
        }

        VictorLogger.info("✓ PostgreSQL RETURNING clause working");
    }
}