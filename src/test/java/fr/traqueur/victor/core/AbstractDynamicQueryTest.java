package fr.traqueur.victor.core;

import fr.traqueur.victor.entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractDynamicQueryTest extends AbstractVictorTest {

    @Test
    void testFindByUsername() {
        String username = "query_" + System.nanoTime();
        userRepository.save(new UserEntity(null, username,
                username + "@test.com", 25, true, "Query Test"));

        assertTrue(userRepository.findByUsername(username).isPresent());
    }

    @Test
    void testFindByUsernameNotFound() {
        Optional<UserEntity> result = userRepository.findByUsername("nonexistent_" + System.nanoTime());
        assertFalse(result.isPresent());
    }

    @Test
    void testFindByAgeGreaterThan() {
        userRepository.save(new UserEntity(null, "u1", "u1@test.com", 20, true, "U1"));
        userRepository.save(new UserEntity(null, "u2", "u2@test.com", 30, true, "U2"));

        List<UserEntity> result = userRepository.findByAgeGreaterThan(25);

        assertFalse(result.isEmpty());
        result.forEach(u -> assertTrue(u.age() > 25));
    }

    @Test
    void testFindByAgeLessThan() {
        userRepository.save(new UserEntity(null, "lt_" + System.nanoTime(), "lt@test.com", 15, true, "LT"));

        List<UserEntity> result = userRepository.findByAgeLessThan(20);

        assertFalse(result.isEmpty());
        result.forEach(u -> assertTrue(u.age() < 20));
    }

    @Test
    void testFindByUsernameAndEmail() {
        String username = "and_" + System.nanoTime();
        String email = username + "@test.com";
        userRepository.save(new UserEntity(null, username, email, 25, true, "And Test"));

        Optional<UserEntity> found = userRepository.findByUsernameAndEmail(username, email);
        assertTrue(found.isPresent());
        assertEquals(username, found.get().username());
    }

    @Test
    void testFindByUsernameOrEmail() {
        String username = "or_" + System.nanoTime();
        String email = username + "@test.com";
        userRepository.save(new UserEntity(null, username, email, 25, true, "Or Test"));

        // Recherche par username uniquement (email différent)
        List<UserEntity> result = userRepository.findByUsernameOrEmail(username, "nobody@test.com");
        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(u -> u.username().equals(username)));
    }

    @Test
    void testFindByNameLike() {
        String unique = "Like" + System.nanoTime();
        userRepository.save(new UserEntity(null, "like_" + System.nanoTime(), "like@test.com", 25, true, unique + "User"));

        // Le framework ajoute automatiquement les % quand l'argument ne contient pas de wildcard
        List<UserEntity> result = userRepository.findByNameLike(unique);

        assertFalse(result.isEmpty());
        result.forEach(u -> assertTrue(u.name().contains(unique)));
    }

    @Test
    void testFindByEmailIsNull() {
        String username = "nullemail_" + System.nanoTime();
        userRepository.save(new UserEntity(null, username, null, 25, true, "Null Email"));

        List<UserEntity> result = userRepository.findByEmailIsNull();

        assertFalse(result.isEmpty());
        result.forEach(u -> assertNull(u.email()));
    }

    @Test
    void testFindByEmailIsNotNull() {
        userRepository.save(new UserEntity(null, "notnull_" + System.nanoTime(), "nn@test.com", 25, true, "Not Null"));

        List<UserEntity> result = userRepository.findByEmailIsNotNull();

        assertFalse(result.isEmpty());
        result.forEach(u -> assertNotNull(u.email()));
    }

    @Test
    void testFindByActiveOrderByUsernameAsc() {
        String prefix = "ord_" + System.nanoTime() + "_";
        userRepository.save(new UserEntity(null, prefix + "beta", "b@test.com", 25, true, "Beta"));
        userRepository.save(new UserEntity(null, prefix + "alpha", "a@test.com", 25, true, "Alpha"));

        List<UserEntity> result = userRepository.findByActiveOrderByUsernameAsc(true);

        assertFalse(result.isEmpty());
        // Vérifie que les usernames commençant par prefix sont ordonnés
        List<String> names = result.stream()
                .map(UserEntity::username)
                .filter(u -> u.startsWith(prefix))
                .toList();
        assertEquals(2, names.size());
        assertTrue(names.get(0).compareTo(names.get(1)) <= 0);
    }

    @Test
    void testFindByActiveOrderByUsernameDesc() {
        String prefix = "desc_" + System.nanoTime() + "_";
        userRepository.save(new UserEntity(null, prefix + "alpha", "da@test.com", 25, true, "Alpha"));
        userRepository.save(new UserEntity(null, prefix + "beta", "db@test.com", 25, true, "Beta"));

        List<UserEntity> result = userRepository.findByActiveOrderByUsernameDesc(true);

        assertFalse(result.isEmpty());
        List<String> names = result.stream()
                .map(UserEntity::username)
                .filter(u -> u.startsWith(prefix))
                .toList();
        assertEquals(2, names.size());
        assertTrue(names.get(0).compareTo(names.get(1)) >= 0);
    }

    // ─── IN avec collection ─────────────────────────────────────────────────────

    @Test
    void testFindByIdIn() {
        String prefix = "in_" + System.nanoTime() + "_";
        UserEntity a = userRepository.save(new UserEntity(null, prefix + "a", "a@test.com", 25, true, "A"));
        UserEntity b = userRepository.save(new UserEntity(null, prefix + "b", "b@test.com", 25, true, "B"));
        UserEntity c = userRepository.save(new UserEntity(null, prefix + "c", "c@test.com", 25, true, "C"));

        List<UserEntity> result = userRepository.findByIdIn(List.of(a.id(), c.id()));

        List<Long> ids = result.stream().map(UserEntity::id).toList();
        assertTrue(ids.contains(a.id()));
        assertTrue(ids.contains(c.id()));
        assertFalse(ids.contains(b.id()));
    }

    @Test
    void testFindByIdInEmptyReturnsEmpty() {
        userRepository.save(new UserEntity(null, "empin_" + System.nanoTime(), "e@test.com", 25, true, "E"));

        List<UserEntity> result = userRepository.findByIdIn(List.of());

        assertTrue(result.isEmpty());
    }

    // ─── existsBy / countBy / deleteBy dérivés ──────────────────────────────────

    @Test
    void testExistsByUsername() {
        String username = "exists_" + System.nanoTime();
        userRepository.save(new UserEntity(null, username, "e@test.com", 25, true, "Exists"));

        assertTrue(userRepository.existsByUsername(username));
        assertFalse(userRepository.existsByUsername("nope_" + System.nanoTime()));
    }

    @Test
    void testCountByAgeGreaterThan() {
        userRepository.save(new UserEntity(null, "cnt1_" + System.nanoTime(), "c1@test.com", 9001, true, "C1"));
        userRepository.save(new UserEntity(null, "cnt2_" + System.nanoTime(), "c2@test.com", 9002, true, "C2"));

        long count = userRepository.countByAgeGreaterThan(9000);

        assertTrue(count >= 2);
    }

    @Test
    void testDeleteByUsername() {
        String username = "del_" + System.nanoTime();
        userRepository.save(new UserEntity(null, username, "d@test.com", 25, true, "Del"));
        assertTrue(userRepository.existsByUsername(username));

        int deleted = userRepository.deleteByUsername(username);

        assertEquals(1, deleted);
        assertFalse(userRepository.existsByUsername(username));
    }
}