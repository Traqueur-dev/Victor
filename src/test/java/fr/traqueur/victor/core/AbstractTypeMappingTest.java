package fr.traqueur.victor.core;

import fr.traqueur.victor.entity.TypesEntity;
import fr.traqueur.victor.model.Priority;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractTypeMappingTest extends AbstractVictorTest {

    @Test
    void testScalarTypesRoundTrip() {
        UUID externalId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 6, 28);
        LocalTime time = LocalTime.of(14, 30, 15);
        BigDecimal price = new BigDecimal("1234.56");

        TypesEntity saved = typesRepo.save(new TypesEntity(
                null, price, date, time, (short) 1234, (byte) 42, Priority.HIGH, externalId));

        assertNotNull(saved.id());

        TypesEntity loaded = typesRepo.findById(saved.id()).orElseThrow();

        assertEquals(0, price.compareTo(loaded.priceAmount()), "BigDecimal mismatch");
        assertEquals(date, loaded.eventDate(), "LocalDate mismatch");
        assertEquals(time, loaded.eventTime(), "LocalTime mismatch");
        assertEquals(Short.valueOf((short) 1234), loaded.smallVal(), "Short mismatch");
        assertEquals(Byte.valueOf((byte) 42), loaded.tinyVal(), "Byte mismatch");
        assertEquals(Priority.HIGH, loaded.priority(), "enum mismatch");
        assertEquals(externalId, loaded.externalId(), "UUID mismatch");
    }
}
