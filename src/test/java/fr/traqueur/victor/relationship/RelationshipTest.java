package fr.traqueur.victor.relationship;

import fr.traqueur.victor.annotations.FetchType;
import fr.traqueur.victor.entity.metadata.EntityMetadata;
import fr.traqueur.victor.entity.metadata.RelationshipMetadata;
import fr.traqueur.victor.entity.AuthorEntity;
import fr.traqueur.victor.entity.BookEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RelationshipTest {

    @Test
    @Order(1)
    @DisplayName("Metadata: BookEntity has a @ManyToOne relationship")
    void testBookEntityManyToOneMetadata() {
        EntityMetadata meta = EntityMetadata.of(BookEntity.class);

        assertEquals("books", meta.getTableName());

        List<RelationshipMetadata> rels = meta.getRelationships();
        assertEquals(1, rels.size(), "BookEntity should have exactly one relationship");

        RelationshipMetadata rel = rels.getFirst();
        assertEquals(RelationshipMetadata.RelationType.MANY_TO_ONE, rel.getType());
        assertEquals("author", rel.getFieldName());
        assertEquals(AuthorEntity.class, rel.getTargetEntityClass());
        assertEquals("author_id", rel.getForeignKeyColumn());
        assertEquals(FetchType.EAGER, rel.getFetchType());
        assertTrue(rel.ownsForeignKey(), "ManyToOne should own the FK column");
        assertFalse(rel.isCollection());
    }

    @Test
    @Order(2)
    @DisplayName("Metadata: AuthorEntity has a @OneToMany relationship")
    void testAuthorEntityOneToManyMetadata() {
        EntityMetadata meta = EntityMetadata.of(AuthorEntity.class);

        assertEquals("authors", meta.getTableName());

        List<RelationshipMetadata> rels = meta.getRelationships();
        assertEquals(1, rels.size(), "AuthorEntity should have exactly one relationship");

        RelationshipMetadata rel = rels.getFirst();
        assertEquals(RelationshipMetadata.RelationType.ONE_TO_MANY, rel.getType());
        assertEquals("books", rel.getFieldName());
        assertEquals(BookEntity.class, rel.getTargetEntityClass());
        assertEquals("author", rel.getMappedByField());
        assertEquals(FetchType.EAGER, rel.getFetchType());
        assertFalse(rel.ownsForeignKey(), "OneToMany should not own the FK column");
        assertTrue(rel.isCollection());
    }

    @Test
    @Order(3)
    @DisplayName("Metadata: BookEntity scalar fields don't include the FK as a regular column")
    void testBookEntityScalarFieldsExcludeRelation() {
        EntityMetadata meta = EntityMetadata.of(BookEntity.class);

        // Scalar fields: only id and title (author is a relationship)
        assertEquals(2, meta.getScalarFields().size());
        assertNotNull(meta.getFieldByName("id"));
        assertNotNull(meta.getFieldByName("title"));
        assertNull(meta.getFieldByName("author"), "author is a relationship, not a scalar field");
    }

    @Test
    @Order(4)
    @DisplayName("Metadata: getAllPersistableFields includes synthetic FK author_id")
    void testBookEntityAllPersistableFields() {
        EntityMetadata meta = EntityMetadata.of(BookEntity.class);

        // All persistable: id + title + author_id (synthetic FK)
        List<?> allFields = meta.getAllPersistableFields();
        assertEquals(3, allFields.size());

        long fkCount = meta.getAllPersistableNonIdFields().stream()
                .filter(f -> f.getColumnName().equals("author_id"))
                .count();
        assertEquals(1, fkCount, "author_id synthetic FK should be in persistable fields");
    }

}
