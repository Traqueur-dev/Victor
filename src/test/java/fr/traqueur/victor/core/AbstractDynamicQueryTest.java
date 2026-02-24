package fr.traqueur.victor.core;

import fr.traqueur.victor.dto.UserDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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
    void testFindByUsernameNotFound() {
        Optional<UserDto> result = userRepository.findByUsername("nonexistent_" + System.nanoTime());
        assertFalse(result.isPresent());
    }

    @Test
    void testFindByAgeGreaterThan() {
        userRepository.save(new UserDto(null, "u1", "u1@test.com", 20, true, "U1"));
        userRepository.save(new UserDto(null, "u2", "u2@test.com", 30, true, "U2"));

        List<UserDto> result = userRepository.findByAgeGreaterThan(25);

        assertFalse(result.isEmpty());
        result.forEach(u -> assertTrue(u.age() > 25));
    }

    @Test
    void testFindByAgeLessThan() {
        userRepository.save(new UserDto(null, "lt_" + System.nanoTime(), "lt@test.com", 15, true, "LT"));

        List<UserDto> result = userRepository.findByAgeLessThan(20);

        assertFalse(result.isEmpty());
        result.forEach(u -> assertTrue(u.age() < 20));
    }

    @Test
    void testFindByUsernameAndEmail() {
        String username = "and_" + System.nanoTime();
        String email = username + "@test.com";
        userRepository.save(new UserDto(null, username, email, 25, true, "And Test"));

        Optional<UserDto> found = userRepository.findByUsernameAndEmail(username, email);
        assertTrue(found.isPresent());
        assertEquals(username, found.get().username());
    }

    @Test
    void testFindByUsernameOrEmail() {
        String username = "or_" + System.nanoTime();
        String email = username + "@test.com";
        userRepository.save(new UserDto(null, username, email, 25, true, "Or Test"));

        // Recherche par username uniquement (email différent)
        List<UserDto> result = userRepository.findByUsernameOrEmail(username, "nobody@test.com");
        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(u -> u.username().equals(username)));
    }

    @Test
    void testFindByNameLike() {
        String unique = "Like" + System.nanoTime();
        userRepository.save(new UserDto(null, "like_" + System.nanoTime(), "like@test.com", 25, true, unique + "User"));

        // Le framework ajoute automatiquement les % quand l'argument ne contient pas de wildcard
        List<UserDto> result = userRepository.findByNameLike(unique);

        assertFalse(result.isEmpty());
        result.forEach(u -> assertTrue(u.name().contains(unique)));
    }

    @Test
    void testFindByEmailIsNull() {
        String username = "nullemail_" + System.nanoTime();
        userRepository.save(new UserDto(null, username, null, 25, true, "Null Email"));

        List<UserDto> result = userRepository.findByEmailIsNull();

        assertFalse(result.isEmpty());
        result.forEach(u -> assertNull(u.email()));
    }

    @Test
    void testFindByEmailIsNotNull() {
        userRepository.save(new UserDto(null, "notnull_" + System.nanoTime(), "nn@test.com", 25, true, "Not Null"));

        List<UserDto> result = userRepository.findByEmailIsNotNull();

        assertFalse(result.isEmpty());
        result.forEach(u -> assertNotNull(u.email()));
    }

    @Test
    void testFindByActiveOrderByUsernameAsc() {
        String prefix = "ord_" + System.nanoTime() + "_";
        userRepository.save(new UserDto(null, prefix + "beta", "b@test.com", 25, true, "Beta"));
        userRepository.save(new UserDto(null, prefix + "alpha", "a@test.com", 25, true, "Alpha"));

        List<UserDto> result = userRepository.findByActiveOrderByUsernameAsc(true);

        assertFalse(result.isEmpty());
        // Vérifie que les usernames commençant par prefix sont ordonnés
        List<String> names = result.stream()
                .map(UserDto::username)
                .filter(u -> u.startsWith(prefix))
                .toList();
        assertEquals(2, names.size());
        assertTrue(names.get(0).compareTo(names.get(1)) <= 0);
    }

    @Test
    void testFindByActiveOrderByUsernameDesc() {
        String prefix = "desc_" + System.nanoTime() + "_";
        userRepository.save(new UserDto(null, prefix + "alpha", "da@test.com", 25, true, "Alpha"));
        userRepository.save(new UserDto(null, prefix + "beta", "db@test.com", 25, true, "Beta"));

        List<UserDto> result = userRepository.findByActiveOrderByUsernameDesc(true);

        assertFalse(result.isEmpty());
        List<String> names = result.stream()
                .map(UserDto::username)
                .filter(u -> u.startsWith(prefix))
                .toList();
        assertEquals(2, names.size());
        assertTrue(names.get(0).compareTo(names.get(1)) >= 0);
    }
}