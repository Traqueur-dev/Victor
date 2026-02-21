package fr.traqueur.victor.core;

import fr.traqueur.victor.*;
import fr.traqueur.victor.dto.ProductDto;
import fr.traqueur.victor.dto.UserDto;
import fr.traqueur.victor.dto.UserV2Dto;
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
                .dtos(UserDto.class)
                .build();

        UserRepository repo1 = v1.createRepository(UserRepository.class);

        repo1.save(new UserDto(
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
                .dtos(UserV2Dto.class)
                .build();

        UserV2Repository repo2 = v2.createRepository(UserV2Repository.class);

        List<UserV2Dto> all = repo2.findAll();

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
                .dtos(UserDto.class)
                .build();

        String url = v1.getConfiguration().connectionUrl();
        v1.close();

        Victor v2 = Victor.configure()
                .url(url)
                .autoDetectDialect()
                .autoMigrate()
                .dtos(UserV2Dto.class)
                .build();

        UserV2Repository repo = v2.createRepository(UserV2Repository.class);

        UserV2Dto saved = repo.save(
                new UserV2Dto(
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
                .dtos(UserDto.class)
                .build();

        UserRepository repo1 = v1.createRepository(UserRepository.class);

        repo1.save(new UserDto(null, "u1_" + System.nanoTime(), "u1@test.com", 25, true, "U1"));
        repo1.save(new UserDto(null, "u2_" + System.nanoTime(), "u2@test.com", 35, false, "U2"));

        long countBefore = repo1.count();

        String url = v1.getConfiguration().connectionUrl();
        v1.close();

        Victor v2 = Victor.configure()
                .url(url)
                .autoDetectDialect()
                .autoMigrate()
                .dtos(UserV2Dto.class)
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
                .dtos(ProductDto.class)
                .build();

        ProductRepository repo = victor.createRepository(ProductRepository.class);

        ProductDto saved = repo.save(
                new ProductDto(
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