package fr.traqueur.victor.conversion;

import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.entity.Model;
import fr.traqueur.victor.entity.UserEntity;
import fr.traqueur.victor.exceptions.VictorConversionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VictorConverterValidationTest {

    static class BadModel implements Model<Long> {
        private Long id;
        @Override public Long getId() { return id; }
        @Override public void setId(Long id) { this.id = id; }
    }

    /** Valid record entity, but missing the static {@code fromModel(BadModel)} companion. */
    @Table(table = "bad")
    record BadEntity(@Id Long id, @Column String name) implements Entity<BadModel> {
        @Override public BadModel toModel() { return new BadModel(); }
    }

    @Test
    void assertConvertibleThrowsWhenFromModelMissing() {
        VictorConversionException ex = assertThrows(VictorConversionException.class,
                () -> VictorConverter.assertConvertible(BadEntity.class));
        assertTrue(ex.getMessage().contains("fromModel"), ex.getMessage());
    }

    @Test
    void assertConvertiblePassesForValidEntity() {
        assertDoesNotThrow(() -> VictorConverter.assertConvertible(UserEntity.class));
    }
}
