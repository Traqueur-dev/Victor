package fr.traqueur.victor.database.query;

import fr.traqueur.victor.entity.Query;
import fr.traqueur.victor.exceptions.VictorException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MethodNameParserTest {

    interface TestRepo {
        Optional<Object> findByUsername(String username);
        List<Object> findByAgeGreaterThan(int age);
        List<Object> findByAgeLessThan(int age);
        List<Object> findByAgeGreaterThanEqual(int age);
        List<Object> findByAgeLessThanEqual(int age);
        List<Object> findByAgeNotEqual(int age);
        List<Object> findByUsernameAndEmail(String username, String email);
        List<Object> findByUsernameOrEmail(String username, String email);
        List<Object> findByActiveOrderByUsernameAsc(boolean active);
        List<Object> findByActiveOrderByUsernameDesc(boolean active);
        List<Object> findByUsernameOrderByAgeAscAndNameDesc(String username);
        List<Object> findByEmailIsNull();
        List<Object> findByEmailIsNotNull();
        List<Object> findByNameLike(String pattern);
        List<Object> findByNameNotLike(String pattern);
        List<Object> findByIdIn(List<Long> ids);
        List<Object> findByIdNotIn(List<Long> ids);
        // Préfixes dérivés
        long countByActive(boolean active);
        boolean existsByUsername(String username);
        int deleteByAgeLessThan(int age);
        // Champ contenant "Or" : casse le split naïf, géré par le tokenizer field-aware
        List<Object> findByIsOrdered(boolean ordered);
        // Cas d'erreur
        void doSomething();
        List<Object> findByUsernameWrongParamCount();
    }

    private static final Set<String> FIELDS =
            Set.of("id", "username", "email", "age", "active", "name", "isOrdered");

    private Method method(String name, Class<?>... params) throws NoSuchMethodException {
        return TestRepo.class.getMethod(name, params);
    }

    // ─── Opérateur Equal (défaut) ───────────────────────────────────────────────

    @Test
    void testSimpleEqual() throws Exception {
        var parsed = MethodNameParser.parse(method("findByUsername", String.class));

        assertEquals(1, parsed.conditions().size());
        assertEquals("Username", parsed.conditions().get(0).fieldName());
        assertEquals("Equal", parsed.conditions().get(0).operator());
        assertNull(parsed.conditions().get(0).connector());
        assertTrue(parsed.conditions().get(0).requiresParameter());
    }

    // ─── Opérateurs de comparaison ──────────────────────────────────────────────

    @Test
    void testGreaterThan() throws Exception {
        var parsed = MethodNameParser.parse(method("findByAgeGreaterThan", int.class));
        assertEquals("GreaterThan", parsed.conditions().get(0).operator());
        assertEquals("Age", parsed.conditions().get(0).fieldName());
    }

    @Test
    void testLessThan() throws Exception {
        var parsed = MethodNameParser.parse(method("findByAgeLessThan", int.class));
        assertEquals("LessThan", parsed.conditions().get(0).operator());
    }

    @Test
    void testGreaterThanEqual() throws Exception {
        var parsed = MethodNameParser.parse(method("findByAgeGreaterThanEqual", int.class));
        assertEquals("GreaterThanEqual", parsed.conditions().get(0).operator());
    }

    @Test
    void testLessThanEqual() throws Exception {
        var parsed = MethodNameParser.parse(method("findByAgeLessThanEqual", int.class));
        assertEquals("LessThanEqual", parsed.conditions().get(0).operator());
    }

    @Test
    void testNotEqual() throws Exception {
        var parsed = MethodNameParser.parse(method("findByAgeNotEqual", int.class));
        assertEquals("NotEqual", parsed.conditions().get(0).operator());
    }

    // ─── Opérateurs Like ────────────────────────────────────────────────────────

    @Test
    void testLike() throws Exception {
        var parsed = MethodNameParser.parse(method("findByNameLike", String.class));
        assertEquals("Like", parsed.conditions().get(0).operator());
        assertTrue(parsed.conditions().get(0).requiresParameter());
    }

    @Test
    void testNotLike() throws Exception {
        var parsed = MethodNameParser.parse(method("findByNameNotLike", String.class));
        assertEquals("NotLike", parsed.conditions().get(0).operator());
    }

    // ─── Opérateurs In / NotIn ──────────────────────────────────────────────────

    @Test
    void testIn() throws Exception {
        var parsed = MethodNameParser.parse(method("findByIdIn", List.class));
        assertEquals("In", parsed.conditions().get(0).operator());
    }

    @Test
    void testNotIn() throws Exception {
        var parsed = MethodNameParser.parse(method("findByIdNotIn", List.class));
        assertEquals("NotIn", parsed.conditions().get(0).operator());
    }

    // ─── Opérateurs Null ────────────────────────────────────────────────────────

    @Test
    void testIsNull() throws Exception {
        var parsed = MethodNameParser.parse(method("findByEmailIsNull"));

        assertEquals("IsNull", parsed.conditions().get(0).operator());
        assertFalse(parsed.conditions().get(0).requiresParameter());
        assertEquals(0, parsed.orderBy().size());
    }

    @Test
    void testIsNotNull() throws Exception {
        var parsed = MethodNameParser.parse(method("findByEmailIsNotNull"));

        assertEquals("IsNotNull", parsed.conditions().get(0).operator());
        assertFalse(parsed.conditions().get(0).requiresParameter());
    }

    // ─── Connecteurs AND / OR ───────────────────────────────────────────────────

    @Test
    void testAndConnector() throws Exception {
        var parsed = MethodNameParser.parse(method("findByUsernameAndEmail", String.class, String.class));

        assertEquals(2, parsed.conditions().size());
        assertEquals("AND", parsed.conditions().get(0).connector());
        assertNull(parsed.conditions().get(1).connector());
    }

    @Test
    void testOrConnector() throws Exception {
        var parsed = MethodNameParser.parse(method("findByUsernameOrEmail", String.class, String.class));

        assertEquals(2, parsed.conditions().size());
        assertEquals("OR", parsed.conditions().get(0).connector());
    }

    // ─── ORDER BY ───────────────────────────────────────────────────────────────

    @Test
    void testOrderByAsc() throws Exception {
        var parsed = MethodNameParser.parse(method("findByActiveOrderByUsernameAsc", boolean.class));

        assertEquals(1, parsed.conditions().size());
        assertEquals(1, parsed.orderBy().size());
        assertEquals("Username", parsed.orderBy().get(0).fieldName());
        assertEquals(Query.Order.ASC, parsed.orderBy().get(0).direction());
    }

    @Test
    void testOrderByDesc() throws Exception {
        var parsed = MethodNameParser.parse(method("findByActiveOrderByUsernameDesc", boolean.class));

        assertEquals(1, parsed.orderBy().size());
        assertEquals(Query.Order.DESC, parsed.orderBy().get(0).direction());
    }

    @Test
    void testOrderByMultipleFields() throws Exception {
        var parsed = MethodNameParser.parse(method("findByUsernameOrderByAgeAscAndNameDesc", String.class));

        assertEquals(2, parsed.orderBy().size());
        assertEquals("Age", parsed.orderBy().get(0).fieldName());
        assertEquals(Query.Order.ASC, parsed.orderBy().get(0).direction());
        assertEquals("Name", parsed.orderBy().get(1).fieldName());
        assertEquals(Query.Order.DESC, parsed.orderBy().get(1).direction());
    }

    // ─── QueryKind / préfixes dérivés ───────────────────────────────────────────

    @Test
    void testKindFind() throws Exception {
        var parsed = MethodNameParser.parse(method("findByUsername", String.class), FIELDS);
        assertEquals(MethodNameParser.QueryKind.FIND, parsed.kind());
    }

    @Test
    void testKindCount() throws Exception {
        var parsed = MethodNameParser.parse(method("countByActive", boolean.class), FIELDS);
        assertEquals(MethodNameParser.QueryKind.COUNT, parsed.kind());
        assertEquals("Active", parsed.conditions().get(0).fieldName());
        assertEquals("Equal", parsed.conditions().get(0).operator());
    }

    @Test
    void testKindExists() throws Exception {
        var parsed = MethodNameParser.parse(method("existsByUsername", String.class), FIELDS);
        assertEquals(MethodNameParser.QueryKind.EXISTS, parsed.kind());
        assertEquals("Username", parsed.conditions().get(0).fieldName());
    }

    @Test
    void testKindDelete() throws Exception {
        var parsed = MethodNameParser.parse(method("deleteByAgeLessThan", int.class), FIELDS);
        assertEquals(MethodNameParser.QueryKind.DELETE, parsed.kind());
        assertEquals("Age", parsed.conditions().get(0).fieldName());
        assertEquals("LessThan", parsed.conditions().get(0).operator());
    }

    // ─── Tokenizer field-aware : champ contenant "Or" ───────────────────────────

    @Test
    void testTokenizerDisambiguatesOrInsideFieldName() throws Exception {
        // Avec la connaissance des champs, "IsOrdered" est un seul champ, pas "Is" OR "dered".
        var parsed = MethodNameParser.parse(method("findByIsOrdered", boolean.class), FIELDS);
        assertEquals(1, parsed.conditions().size());
        assertEquals("IsOrdered", parsed.conditions().get(0).fieldName());
        assertEquals("Equal", parsed.conditions().get(0).operator());
        assertNull(parsed.conditions().get(0).connector());
    }

    @Test
    void testLegacySplitMisparsesOrInFieldName() throws Exception {
        // Sans champs connus, l'heuristique legacy casse sur le "Or" interne : "IsOrdered"
        // devient "Is" OR "dered" (2 conditions) → 2 paramètres attendus pour 1 réel → erreur.
        assertThrows(VictorException.class, () ->
                MethodNameParser.parse(method("findByIsOrdered", boolean.class)));
    }

    // ─── Cas d'erreur ───────────────────────────────────────────────────────────

    @Test
    void testMethodNotStartingWithFindByThrows() throws Exception {
        assertThrows(VictorException.class, () ->
            MethodNameParser.parse(method("doSomething"))
        );
    }

    @Test
    void testWrongParameterCountThrows() throws Exception {
        // findByUsernameWrongParamCount() a 0 paramètres mais Username/Equal en requiert 1
        assertThrows(VictorException.class, () ->
            MethodNameParser.parse(method("findByUsernameWrongParamCount"))
        );
    }
}