package fr.traqueur.victor.dialect.h2;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.VictorBuilder;
import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.core.AbstractTestRunner;
import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.entity.Model;
import fr.traqueur.victor.entity.Repository;
import fr.traqueur.victor.entity.Service;
import fr.traqueur.victor.entity.UserEntity;
import fr.traqueur.victor.exceptions.VictorConversionException;
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