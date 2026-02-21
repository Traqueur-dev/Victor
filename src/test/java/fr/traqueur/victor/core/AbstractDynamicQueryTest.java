package fr.traqueur.victor.core;

import fr.traqueur.victor.dto.UserDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractDynamicQueryTest extends AbstractVictorTest {

    @Test
    void testFindByUsername() {
        String username = "query_" + System.nanoTime();
        userRepository.save(new UserDto(null, username,
                username + "@test.com", 25, true, "Query Test"));

        assertTrue(userRepository.findByUsername(username).isPresent());
    }

    @Test
    void testFindByAgeGreaterThan() {
        userRepository.save(new UserDto(null, "u1", "u1@test.com", 20, true, "U1"));
        userRepository.save(new UserDto(null, "u2", "u2@test.com", 30, true, "U2"));

        List<UserDto> result = userRepository.findByAgeGreaterThan(25);

        assertFalse(result.isEmpty());
        result.forEach(u -> assertTrue(u.age() > 25));
    }
}