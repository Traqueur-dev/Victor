package fr.traqueur.victor.core;

import fr.traqueur.victor.entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractCrudTest extends AbstractVictorTest {

    protected UserEntity createUser(String username) {
        return new UserEntity(null, username,
                username + "@test.com",
                25, true, "Test " + username);
    }

    @Test
    void testSaveAndFind() {
        UserEntity saved = userRepository.save(createUser("save_" + System.nanoTime()));

        assertNotNull(saved.id());

        Optional<UserEntity> found = userRepository.findById(saved.id());
        assertTrue(found.isPresent());
    }

    @Test
    void testUpdate() {
        UserEntity saved = userRepository.save(createUser("update_" + System.nanoTime()));

        UserEntity updated = new UserEntity(
                saved.id(),
                saved.username(),
                "updated@test.com",
                30,
                false,
                saved.name()
        );

        userRepository.save(updated);

        Optional<UserEntity> found = userRepository.findById(saved.id());
        assertEquals("updated@test.com", found.get().email());
    }

    @Test
    void testDelete() {
        UserEntity saved = userRepository.save(createUser("delete_" + System.nanoTime()));
        Long id = saved.id();

        userRepository.deleteById(id);

        assertFalse(userRepository.existsById(id));
    }

    @Test
    void testFindByIdNotFound() {
        // Un ID très grand qui n'existe probablement pas
        Optional<UserEntity> result = userRepository.findById(Long.MAX_VALUE);
        assertFalse(result.isPresent());
    }

    @Test
    void testExistsByIdReturnsFalseForUnknownId() {
        assertFalse(userRepository.existsById(Long.MAX_VALUE - 1));
    }

    @Test
    void testCountIncrementsOnSave() {
        long before = userRepository.count();
        userRepository.save(createUser("count_" + System.nanoTime()));
        assertEquals(before + 1, userRepository.count());
    }

    @Test
    void testFindAll() {
        userRepository.save(createUser("all_a_" + System.nanoTime()));
        userRepository.save(createUser("all_b_" + System.nanoTime()));

        assertFalse(userRepository.findAll().isEmpty());
    }
}