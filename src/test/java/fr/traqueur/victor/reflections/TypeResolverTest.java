package fr.traqueur.victor.reflections;

import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.exceptions.VictorException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypeResolverTest {

    static class TestModel implements Entity<Long> {
        private Long id;
        @Override public Long getId() { return id; }
        @Override public void setId(Long id) { this.id = id; }
    }

    record TestDto(Long id) implements Dto<TestModel> {
        @Override public TestModel toModel() { return new TestModel(); }
    }

    interface TestRepository extends Repository<TestDto, TestModel, Long> {}

    interface TestService extends Service<TestModel, TestDto, Long, TestRepository> {}

    // Repository avec String comme ID
    static class StringModel implements Entity<String> {
        private String id;
        @Override public String getId() { return id; }
        @Override public void setId(String id) { this.id = id; }
    }

    record StringDto(String id) implements Dto<StringModel> {
        @Override public StringModel toModel() { return new StringModel(); }
    }

    interface StringRepository extends Repository<StringDto, StringModel, String> {}

    // ─── Repository type resolution ─────────────────────────────────────────────

    @Test
    void testResolveRepositoryDtoClass() {
        var info = TypeResolver.resolveRepositoryTypes(TestRepository.class);
        assertEquals(TestDto.class, info.dtoClass());
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
        assertEquals(StringDto.class, info.dtoClass());
        assertEquals(StringModel.class, info.modelClass());
    }

    // ─── Service type resolution ─────────────────────────────────────────────────

    @Test
    void testResolveServiceModelClass() {
        var info = TypeResolver.resolveServiceTypes(TestService.class);
        assertEquals(TestModel.class, info.modelClass());
    }

    @Test
    void testResolveServiceDtoClass() {
        var info = TypeResolver.resolveServiceTypes(TestService.class);
        assertEquals(TestDto.class, info.dtoClass());
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