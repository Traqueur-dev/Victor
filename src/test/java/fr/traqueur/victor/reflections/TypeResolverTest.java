package fr.traqueur.victor.reflections;

import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.entity.Model;
import fr.traqueur.victor.entity.Repository;
import fr.traqueur.victor.entity.Service;
import fr.traqueur.victor.exceptions.VictorException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypeResolverTest {

    static class TestModel implements Model<Long> {
        private Long id;
        @Override public Long getId() { return id; }
        @Override public void setId(Long id) { this.id = id; }
    }

    record TestEntity(Long id) implements Entity<TestModel> {
        @Override public TestModel toModel() { return new TestModel(); }
    }

    interface TestRepository extends Repository<TestEntity, TestModel, Long> {}

    interface TestService extends Service<TestModel, TestEntity, Long, TestRepository> {}

    // Repository avec String comme ID
    static class StringModel implements Model<String> {
        private String id;
        @Override public String getId() { return id; }
        @Override public void setId(String id) { this.id = id; }
    }

    record StringEntity(String id) implements Entity<StringModel> {
        @Override public StringModel toModel() { return new StringModel(); }
    }

    interface StringRepository extends Repository<StringEntity, StringModel, String> {}

    // ─── Repository type resolution ─────────────────────────────────────────────

    @Test
    void testResolveRepositoryEntityClass() {
        var info = TypeResolver.resolveRepositoryTypes(TestRepository.class);
        assertEquals(TestEntity.class, info.entityClass());
    }

    @Test
    void testResolveRepositoryModelClass() {
        var info = TypeResolver.resolveRepositoryTypes(TestRepository.class);
        assertEquals(TestModel.class, info.modelClass());
    }

    @Test
    void testResolveRepositoryIdClass() {
        var info = TypeResolver.resolveRepositoryTypes(TestRepository.class);
        assertEquals(Long.class, info.idClass());
    }

    @Test
    void testResolveRepositoryWithStringId() {
        var info = TypeResolver.resolveRepositoryTypes(StringRepository.class);
        assertEquals(String.class, info.idClass());
        assertEquals(StringEntity.class, info.entityClass());
        assertEquals(StringModel.class, info.modelClass());
    }

    // ─── Service type resolution ─────────────────────────────────────────────────

    @Test
    void testResolveServiceModelClass() {
        var info = TypeResolver.resolveServiceTypes(TestService.class);
        assertEquals(TestModel.class, info.modelClass());
    }

    @Test
    void testResolveServiceEntityClass() {
        var info = TypeResolver.resolveServiceTypes(TestService.class);
        assertEquals(TestEntity.class, info.entityClass());
    }

    @Test
    void testResolveServiceIdClass() {
        var info = TypeResolver.resolveServiceTypes(TestService.class);
        assertEquals(Long.class, info.idClass());
    }

    @Test
    void testResolveServiceRepositoryClass() {
        var info = TypeResolver.resolveServiceTypes(TestService.class);
        assertEquals(TestRepository.class, info.repositoryClass());
    }

    // ─── Cas d'erreur ───────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("rawtypes")
    void testRepositoryWithoutGenericTypesThrows() {
        interface RawRepository extends Repository {}

        assertThrows(VictorException.class, () ->
            TypeResolver.resolveRepositoryTypes((Class) RawRepository.class)
        );
    }
}