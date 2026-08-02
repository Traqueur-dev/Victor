package fr.traqueur.victor.dialect.h2;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.VictorBuilder;
import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.annotations.VictorIndex;
import fr.traqueur.victor.core.AbstractTestRunner;
import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.entity.Model;
import fr.traqueur.victor.entity.Repository;
import fr.traqueur.victor.entity.Service;
import fr.traqueur.victor.entity.UserEntity;
import fr.traqueur.victor.entity.dialect.h2.H2Dialect;
import fr.traqueur.victor.exceptions.VictorConversionException;
import fr.traqueur.victor.utils.VictorLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;
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
                .entities(UserEntity.class)
                .build();

        var repo = victor.createRepository(
                fr.traqueur.victor.repository.UserRepository.class);

        UserEntity saved = repo.save(
                new UserEntity(null,
                        "mysql_compat_" + System.nanoTime(),
                        "test@test.com",
                        25,
                        true,
                        "Test")
        );

        assertNotNull(saved.id());

        victor.close();
    }

    @Test
    @DisplayName("Config: createService fails fast when the entity has no fromModel")
    void testServiceCreationFailsFast() {
        var victor = Victor.configure()
                .h2()
                .database("valcheck_" + UUID.randomUUID().toString().replace("-", ""))
                .autoMigrate()
                .entities(BadEntity.class)
                .build();
        try {
            VictorConversionException ex = assertThrows(VictorConversionException.class,
                    () -> victor.createService(BadService.class));
            assertTrue(ex.getMessage().contains("fromModel"), ex.getMessage());
        } finally {
            victor.close();
        }
    }

    @Test
    @DisplayName("H2: column introspection sees quoted (lowercase) tables")
    void testListColumnsSeesQuotedLowercaseTables() throws Exception {
        // Victor quotes identifiers at CREATE time, so H2 stores them lowercase
        // case-sensitively. generateListColumnsSQL must still find the columns —
        // regression: it used to filter on TABLE_NAME = 'UPPERCASED' and match nothing,
        // so migration replayed failing ADD COLUMNs on every startup.
        try (var conn = DriverManager.getConnection("jdbc:h2:mem:colcase_" + System.nanoTime())) {
            try (var stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE \"economy_txns\" (" +
                        "\"id\" VARCHAR(64) NOT NULL, " +
                        "\"profile_id\" UUID NOT NULL, " +
                        "PRIMARY KEY (\"id\"))");
            }

            H2Dialect dialect = new H2Dialect();
            Set<String> columns = new HashSet<>();
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery(dialect.generateListColumnsSQL("economy_txns", null))) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }

            assertEquals(Set.of("id", "profile_id"), columns);
        }
    }

    @Test
    @DisplayName("H2: running auto-migration twice on the same database is idempotent")
    void testDoubleMigrationIdempotent() {
        String database = "remigrate_" + UUID.randomUUID().toString().replace("-", "");

        var first = configureVictor()
                .database(database)
                .autoMigrate()
                .entities(UserEntity.class, IndexedEntity.class)
                .build();
        first.close();

        // Second boot on the same (still-open, DB_CLOSE_DELAY=-1) database: migration must
        // see the existing quoted tables/columns and leave the schema usable.
        var second = configureVictor()
                .database(database)
                .autoMigrate()
                .entities(UserEntity.class, IndexedEntity.class)
                .build();
        try {
            var repo = second.createRepository(
                    fr.traqueur.victor.repository.UserRepository.class);
            UserEntity saved = repo.save(
                    new UserEntity(null,
                            "remigrated_" + System.nanoTime(),
                            "remigrate@test.com",
                            30,
                            true,
                            "Test"));
            assertNotNull(saved.id());
        } finally {
            second.close();
        }
    }

    @Test
    @DisplayName("@Query: string and boolean literals survive identifier preprocessing")
    void testQueryLiteralsNotMangled() {
        var victor = configureVictor()
                .autoMigrate()
                .entities(UserEntity.class)
                .build();
        try {
            var repo = victor.createRepository(
                    fr.traqueur.victor.repository.UserRepository.class);
            repo.save(new UserEntity(null, "lit_" + System.nanoTime(), "lit@test.com", 30, true, "Literal"));

            // Before the fix: 'Literal' became '"Literal"' and TRUE became "TRUE" — 0 rows / SQL error.
            assertEquals(1, repo.findByLiteralName().size());
            assertEquals(1, repo.deactivateLiterals());
            assertFalse(repo.findByLiteralName().getFirst().active());
        } finally {
            victor.close();
        }
    }

    // Regression: repeating @VictorIndex from OUTSIDE the annotations package requires the
    // @Repeatable container (VictorIndexes) to be public — this entity fails to compile otherwise.
    @Table(table = "indexed_cfg")
    @VictorIndex(columns = {"status", "created_at"})
    @VictorIndex(columns = {"owner", "status"}, unique = true)
    record IndexedEntity(
            @Id Long id,
            @Column(length = 16) String status,
            @Column(length = 36) String owner,
            @Column(name = "created_at") long createdAt
    ) implements Entity<IndexedModel> {
        @Override public IndexedModel toModel() { return new IndexedModel(); }
        public static IndexedEntity fromModel(IndexedModel model) { return new IndexedEntity(model.getId(), null, null, 0L); }
    }

    static class IndexedModel implements Model<Long> {
        private Long id;
        @Override public Long getId() { return id; }
        @Override public void setId(Long id) { this.id = id; }
    }

    static class BadModel implements Model<Long> {
        private Long id;
        @Override public Long getId() { return id; }
        @Override public void setId(Long id) { this.id = id; }
    }

    // Valid record entity but missing the static fromModel(BadModel) companion.
    @Table(table = "bad_cfg")
    record BadEntity(@Id Long id, @Column String name) implements Entity<BadModel> {
        @Override public BadModel toModel() { return new BadModel(); }
    }

    interface BadRepository extends Repository<BadEntity, BadModel, Long> {}

    interface BadService extends Service<BadModel, BadEntity, Long, BadRepository> {}
}