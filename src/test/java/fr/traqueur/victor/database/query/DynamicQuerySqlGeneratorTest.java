package fr.traqueur.victor.database.query;

import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.entity.Model;
import fr.traqueur.victor.entity.dialect.Dialect;
import fr.traqueur.victor.entity.metadata.EntityMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicQuerySqlGeneratorTest {

    static class TestModel implements Model<Long> {
        private Long id;
        @Override public Long getId() { return id; }
        @Override public void setId(Long id) { this.id = id; }
    }

    @Table(table = "users")
    record TestEntity(
        @Id Long id,
        @Column String username,
        @Column Integer age,
        @Column Boolean active,
        @Column String email,
        @Column String name
    ) implements Entity<TestModel> {
        @Override public TestModel toModel() { return new TestModel(); }
    }

    @Table(table = "users", schema = "myschema")
    record TestEntityWithSchema(
        @Id Long id,
        @Column String username
    ) implements Entity<TestModel> {
        @Override public TestModel toModel() { return new TestModel(); }
    }

    interface TestRepo {
        Optional<Object> findByUsername(String username);
        List<Object> findByAgeGreaterThan(int age);
        List<Object> findByAgeLessThan(int age);
        List<Object> findByAgeGreaterThanEqual(int age);
        List<Object> findByAgeLessThanEqual(int age);
        List<Object> findByAgeNotEqual(int age);
        List<Object> findByUsernameAndAge(String username, int age);
        List<Object> findByUsernameOrAge(String username, int age);
        List<Object> findByActiveOrderByUsernameAsc(boolean active);
        List<Object> findByActiveOrderByUsernameDesc(boolean active);
        List<Object> findByEmailIsNull();
        List<Object> findByEmailIsNotNull();
        List<Object> findByUsernameLike(String pattern);
        List<Object> findByUsernameNotLike(String pattern);
        List<Object> findByIdIn(List<Long> ids);
        List<Object> findByIdNotIn(List<Long> ids);
    }

    @Mock
    private Dialect dialect;

    private EntityMetadata entityMetadata;

    @BeforeEach
    void setUp() {
        entityMetadata = EntityMetadata.of(TestEntity.class);
        // Simule un dialecte avec des backticks (style MySQL)
        when(dialect.quoteIdentifier(anyString())).thenAnswer(inv -> "`" + inv.getArgument(0) + "`");
    }

    private Method method(String name, Class<?>... params) throws NoSuchMethodException {
        return TestRepo.class.getMethod(name, params);
    }

    // ─── Conditions simples ─────────────────────────────────────────────────────

    @Test
    void testEqualQuery() throws Exception {
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByUsername", String.class)));
        assertEquals("SELECT * FROM `users` WHERE `username` = ?", sql);
    }

    @Test
    void testGreaterThanQuery() throws Exception {
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByAgeGreaterThan", int.class)));
        assertEquals("SELECT * FROM `users` WHERE `age` > ?", sql);
    }

    @Test
    void testLessThanQuery() throws Exception {
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByAgeLessThan", int.class)));
        assertEquals("SELECT * FROM `users` WHERE `age` < ?", sql);
    }

    @Test
    void testGreaterThanEqualQuery() throws Exception {
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByAgeGreaterThanEqual", int.class)));
        assertEquals("SELECT * FROM `users` WHERE `age` >= ?", sql);
    }

    @Test
    void testLessThanEqualQuery() throws Exception {
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByAgeLessThanEqual", int.class)));
        assertEquals("SELECT * FROM `users` WHERE `age` <= ?", sql);
    }

    @Test
    void testNotEqualQuery() throws Exception {
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByAgeNotEqual", int.class)));
        assertEquals("SELECT * FROM `users` WHERE `age` != ?", sql);
    }

    @Test
    void testInQuery() throws Exception {
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByIdIn", List.class)));
        assertEquals("SELECT * FROM `users` WHERE `id` IN (?)", sql);
    }

    @Test
    void testNotInQuery() throws Exception {
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByIdNotIn", List.class)));
        assertEquals("SELECT * FROM `users` WHERE `id` NOT IN (?)", sql);
    }

    // ─── Opérateurs Null ────────────────────────────────────────────────────────

    @Test
    void testIsNullQuery() throws Exception {
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByEmailIsNull")));
        assertEquals("SELECT * FROM `users` WHERE `email` IS NULL", sql);
    }

    @Test
    void testIsNotNullQuery() throws Exception {
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByEmailIsNotNull")));
        assertEquals("SELECT * FROM `users` WHERE `email` IS NOT NULL", sql);
    }

    // ─── Opérateurs Like ────────────────────────────────────────────────────────

    @Test
    void testLikeQueryForMysql() throws Exception {
        when(dialect.getName()).thenReturn("mysql");
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByUsernameLike", String.class)));
        assertEquals("SELECT * FROM `users` WHERE `username` LIKE ?", sql);
    }

    @Test
    void testLikeQueryForPostgresql() throws Exception {
        when(dialect.getName()).thenReturn("postgresql");
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByUsernameLike", String.class)));
        assertTrue(sql.contains("LIKE") && sql.contains("ESCAPE"));
    }

    @Test
    void testNotLikeQueryForSqlite() throws Exception {
        when(dialect.getName()).thenReturn("sqlite");
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByUsernameNotLike", String.class)));
        assertTrue(sql.contains("NOT LIKE") && sql.contains("ESCAPE"));
    }

    // ─── Connecteurs AND / OR ───────────────────────────────────────────────────

    @Test
    void testAndQuery() throws Exception {
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByUsernameAndAge", String.class, int.class)));
        assertEquals("SELECT * FROM `users` WHERE `username` = ? AND `age` = ?", sql);
    }

    @Test
    void testOrQuery() throws Exception {
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByUsernameOrAge", String.class, int.class)));
        assertEquals("SELECT * FROM `users` WHERE `username` = ? OR `age` = ?", sql);
    }

    // ─── ORDER BY ───────────────────────────────────────────────────────────────

    @Test
    void testOrderByAscQuery() throws Exception {
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByActiveOrderByUsernameAsc", boolean.class)));
        assertEquals("SELECT * FROM `users` WHERE `active` = ? ORDER BY `username` ASC", sql);
    }

    @Test
    void testOrderByDescQuery() throws Exception {
        var gen = new DynamicQuerySqlGenerator(entityMetadata, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByActiveOrderByUsernameDesc", boolean.class)));
        assertEquals("SELECT * FROM `users` WHERE `active` = ? ORDER BY `username` DESC", sql);
    }

    // ─── Schema ─────────────────────────────────────────────────────────────────

    @Test
    void testQueryWithSchema() throws Exception {
        var metadataWithSchema = EntityMetadata.of(TestEntityWithSchema.class);
        var gen = new DynamicQuerySqlGenerator(metadataWithSchema, dialect, false);
        var sql = gen.generateSql(MethodNameParser.parse(method("findByUsername", String.class)));
        assertEquals("SELECT * FROM `myschema`.`users` WHERE `username` = ?", sql);
    }
}