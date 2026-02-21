package fr.traqueur.victor.dialect.h2;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.VictorBuilder;
import fr.traqueur.victor.core.*;
import fr.traqueur.victor.dto.UserDto;
import fr.traqueur.victor.utils.VictorLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class H2Test {

    // ================================
    // CONFIGURATION UNIQUE DU DIALECT
    // ================================
    protected VictorBuilder configureVictor() {
        String url = System.getProperty("victor.test.url");

        if (url != null) {
            return Victor.configure()
                    .url(url)
                    .autoDetectDialect();
        }

        return Victor.configure()
                .h2()
                .database("testdb_" + System.nanoTime());
    }

    // ====================================================
    // SUITES COMMUNES (réutilisent configureVictor())
    // ====================================================

    @Nested
    class Crud extends AbstractCrudTest {
        @Override protected VictorBuilder configureVictor() {
            return H2Test.this.configureVictor();
        }
    }

    @Nested
    class DynamicQuery extends AbstractDynamicQueryTest {
        @Override protected VictorBuilder configureVictor() {
            return H2Test.this.configureVictor();
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
            return H2Test.this.configureVictor();
        }
    }

    @Nested
    class Migration extends AbstractMigrationTest {
        @Override protected VictorBuilder configureVictor() {
            return H2Test.this.configureVictor();
        }
    }

    @Nested
    class ServiceLayer extends AbstractUserServiceTest {
        @Override protected VictorBuilder configureVictor() {
            return H2Test.this.configureVictor();
        }
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