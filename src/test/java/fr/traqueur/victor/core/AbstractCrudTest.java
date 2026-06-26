package fr.traqueur.victor.core;

import fr.traqueur.victor.entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
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
    void testInheritedColumnIsPersistedAndMapped() {
        var saved = vehicleRepo.save(
                new fr.traqueur.victor.entity.VehicleEntity(null, "Peugeot", "alice"));
        assertNotNull(saved.getId());

        var found = vehicleRepo.findById(saved.getId()).orElseThrow();
        assertEquals("Peugeot", found.getBrand());
        // created_by is declared on the AuditableEntity superclass.
        assertEquals("alice", found.getCreatedBy());
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

    @Test
    void testBatchInsertAssignsIdsAndPersists() {
        long before = userRepository.count();

        List<UserEntity> batch = List.of(
                createUser("batch_a_" + System.nanoTime()),
                createUser("batch_b_" + System.nanoTime()),
                createUser("batch_c_" + System.nanoTime())
        );

        var saved = userRepository.saveAll(batch);

        assertEquals(3, saved.size());
        saved.forEach(u -> assertNotNull(u.id()));
        assertEquals(before + 3, userRepository.count());
    }

    @Test
    void testBatchInsertIsAtomicOnFailure() {
        long before = userRepository.count();
        String duplicated = "dup_" + System.nanoTime();

        // Two rows share the same unique username -> the batch must fail and roll back entirely.
        List<UserEntity> batch = List.of(
                createUser("ok_" + System.nanoTime()),
                createUser(duplicated),
                createUser(duplicated)
        );

        assertThrows(Exception.class, () -> userRepository.saveAll(batch));

        assertEquals(before, userRepository.count());
        assertFalse(userRepository.findByUsername(duplicated).isPresent());
    }
}