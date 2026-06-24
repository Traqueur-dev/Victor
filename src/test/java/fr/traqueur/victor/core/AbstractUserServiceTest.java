package fr.traqueur.victor.core;

import fr.traqueur.victor.*;
import fr.traqueur.victor.entity.UserEntity;
import fr.traqueur.victor.model.User;
import fr.traqueur.victor.repository.UserRepository;
import fr.traqueur.victor.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractUserServiceTest {

    protected Victor victor;
    protected UserService userService;

    protected abstract VictorBuilder configureVictor();

    @BeforeEach
    void setUp() {
        victor = configureVictor()
                .autoMigrate()
                .showSql()
                .entities(UserEntity.class)
                .build();

        userService = victor.createService(UserService.class);
    }

    @AfterEach
    void tearDown() {
        if (victor != null) {
            victor.close();
        }
    }

    protected User createUser(String username) {
        return new User(
                username,
                username + "@test.com",
                25,
                true,
                "Test " + username
        );
    }

    // =============================
    // TESTS COMMUNS
    // =============================

    @org.junit.jupiter.api.Test
    void testSaveUser() {
        User saved = userService.save(createUser("save_" + System.nanoTime()));

        assertNotNull(saved);
        assertNotNull(saved.getId());
    }

    @org.junit.jupiter.api.Test
    void testFindById() {
        User saved = userService.save(createUser("find_" + System.nanoTime()));

        var found = userService.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals(saved.getUsername(), found.get().getUsername());
    }

    @org.junit.jupiter.api.Test
    void testUpdate() {
        User saved = userService.save(createUser("update_" + System.nanoTime()));

        saved.setAge(42);

        User updated = userService.update(saved.getId(), saved);

        assertEquals(42, updated.getAge());
    }

    @org.junit.jupiter.api.Test
    void testDelete() {
        User saved = userService.save(createUser("delete_" + System.nanoTime()));

        userService.deleteById(saved.getId());

        assertFalse(userService.exists(saved.getId()));
    }

    @org.junit.jupiter.api.Test
    void testCount() {
        userService.save(createUser("count1_" + System.nanoTime()));
        userService.save(createUser("count2_" + System.nanoTime()));

        assertTrue(userService.count() >= 2);
    }

    @org.junit.jupiter.api.Test
    void testBulkSave() {
        var list = java.util.List.of(
                createUser("bulk1"),
                createUser("bulk2"),
                createUser("bulk3")
        );

        var saved = userService.saveAll(list);

        assertEquals(3, saved.size());
        saved.forEach(u -> assertNotNull(u.getId()));
    }

    @org.junit.jupiter.api.Test
    void testRepositoryAccess() {
        User saved = userService.save(createUser("repo_" + System.nanoTime()));

        UserRepository repo = userService.repository();

        var found = repo.findByUsername(saved.getUsername());

        assertTrue(found.isPresent());
    }
}