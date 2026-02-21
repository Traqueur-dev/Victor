package fr.traqueur.victor.core;

import fr.traqueur.victor.dto.UserDto;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractCrudTest extends AbstractVictorTest {

    protected UserDto createUser(String username) {
        return new UserDto(null, username,
                username + "@test.com",
                25, true, "Test " + username);
    }

    @Test
    void testSaveAndFind() {
        UserDto saved = userRepository.save(createUser("save_" + System.nanoTime()));

        assertNotNull(saved.id());

        Optional<UserDto> found = userRepository.findById(saved.id());
        assertTrue(found.isPresent());
    }

    @Test
    void testUpdate() {
        UserDto saved = userRepository.save(createUser("update_" + System.nanoTime()));

        UserDto updated = new UserDto(
                saved.id(),
                saved.username(),
                "updated@test.com",
                30,
                false,
                saved.name()
        );

        userRepository.save(updated);

        Optional<UserDto> found = userRepository.findById(saved.id());
        assertEquals("updated@test.com", found.get().email());
    }

    @Test
    void testDelete() {
        UserDto saved = userRepository.save(createUser("delete_" + System.nanoTime()));
        Long id = saved.id();

        userRepository.deleteById(id);

        assertFalse(userRepository.existsById(id));
    }
}