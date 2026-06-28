package fr.traqueur.victor.core;

import fr.traqueur.victor.*;
import fr.traqueur.victor.entity.ProductEntity;
import fr.traqueur.victor.entity.UserEntity;
import fr.traqueur.victor.entity.UserV2Entity;
import fr.traqueur.victor.repository.ProductRepository;
import fr.traqueur.victor.repository.UserRepository;
import fr.traqueur.victor.repository.UserV2Repository;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractMigrationTest extends AbstractVictorTest {

    @Test
    @Order(1)
    @DisplayName("Migration: ALTER TABLE adds new columns")
    void testAlterTableAddColumn() {

        Victor v1 = configureVictor()
                .autoMigrate()
                .entities(UserEntity.class)
                .build();

        UserRepository repo1 = v1.createRepository(UserRepository.class);

        repo1.save(new UserEntity(
                null,
                "alice_" + System.nanoTime(),
                "alice@test.com",
                25,
                true,
                "Alice"
        ));

        String url = v1.getConfiguration().connectionUrl();
        v1.close();

        Victor v2 = Victor.configure()
                .url(url)
                .autoDetectDialect()
                .autoMigrate()
                .entities(UserV2Entity.class)
                .build();

        UserV2Repository repo2 = v2.createRepository(UserV2Repository.class);

        List<UserV2Entity> all = repo2.findAll();

        assertFalse(all.isEmpty());
        assertNull(all.get(0).phone());
        assertNull(all.get(0).bio());

        v2.close();
    }

    @Test
    @Order(2)
    @DisplayName("Migration: Insert with new columns after migration")
    void testInsertWithNewColumns() {

        Victor v1 = configureVictor()
                .autoMigrate()
                .entities(UserEntity.class)
                .build();

        String url = v1.getConfiguration().connectionUrl();
        v1.close();

        Victor v2 = Victor.configure()
                .url(url)
                .autoDetectDialect()
                .autoMigrate()
                .entities(UserV2Entity.class)
                .build();

        UserV2Repository repo = v2.createRepository(UserV2Repository.class);

        UserV2Entity saved = repo.save(
                new UserV2Entity(
                        null,
                        "bob_" + System.nanoTime(),
                        "bob@test.com",
                        30,
                        true,
                        "Bob",
                        "+33123456789",
                        "A developer"
                )
        );

        assertNotNull(saved.id());

        var found = repo.findById(saved.id());
        assertTrue(found.isPresent());
        assertEquals("+33123456789", found.get().phone());
        assertEquals("A developer", found.get().bio());

        v2.close();
    }

    @Test
    @Order(3)
    @DisplayName("Migration: Existing data preserved after migration")
    void testExistingDataPreserved() {

        Victor v1 = configureVictor()
                .autoMigrate()
                .entities(UserEntity.class)
                .build();

        UserRepository repo1 = v1.createRepository(UserRepository.class);

        repo1.save(new UserEntity(null, "u1_" + System.nanoTime(), "u1@test.com", 25, true, "U1"));
        repo1.save(new UserEntity(null, "u2_" + System.nanoTime(), "u2@test.com", 35, false, "U2"));

        long countBefore = repo1.count();

        String url = v1.getConfiguration().connectionUrl();
        v1.close();

        Victor v2 = Victor.configure()
                .url(url)
                .autoDetectDialect()
                .autoMigrate()
                .entities(UserV2Entity.class)
                .build();

        UserV2Repository repo2 = v2.createRepository(UserV2Repository.class);

        long countAfter = repo2.count();
        assertEquals(countBefore, countAfter);

        v2.close();
    }

    @Test
    @Order(4)
    @DisplayName("Migration: Index creation with @VictorIndex")
    void testIndexCreation() {

        Victor victor = configureVictor()
                .autoMigrate()
                .entities(ProductEntity.class)
                .build();

        ProductRepository repo = victor.createRepository(ProductRepository.class);

        ProductEntity saved = repo.save(
                new ProductEntity(
                        null,
                        "Laptop",
                        "Electronics",
                        999.99,
                        "SKU_" + System.nanoTime()
                )
        );

        assertNotNull(saved.id());

        var found = repo.findById(saved.id());
        assertTrue(found.isPresent());

        victor.close();
    }
}