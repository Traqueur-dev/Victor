package fr.traqueur.victor.core;

import fr.traqueur.victor.entity.Audit;
import fr.traqueur.victor.entity.InvoiceEntity;
import fr.traqueur.victor.entity.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractEmbeddedTest extends AbstractVictorTest {

    // Fixed, sub-second-free instant so the round-trip is exact on every dialect
    // (SQLite stores millisecond-precision TEXT timestamps).
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

    @Test
    void testEmbeddedPersistedAndMapped() {
        InvoiceEntity saved = invoiceRepo.save(new InvoiceEntity(
                null, "INV-" + System.nanoTime(),
                new Audit(CREATED_AT, "alice"),
                new Money(100, "EUR"),
                new Money(120, "USD")));

        assertNotNull(saved.id());

        InvoiceEntity found = invoiceRepo.findById(saved.id()).orElseThrow();

        // Embedded never null on read; LocalDateTime round-trips on every dialect.
        assertNotNull(found.audit());
        assertEquals("alice", found.audit().createdBy());
        assertEquals(CREATED_AT, found.audit().createdAt());
    }

    @Test
    void testEmbeddedPrefixDisambiguatesSameType() {
        InvoiceEntity saved = invoiceRepo.save(new InvoiceEntity(
                null, "INV-" + System.nanoTime(),
                new Audit(CREATED_AT, "bob"),
                new Money(100, "EUR"),
                new Money(120, "USD")));

        InvoiceEntity found = invoiceRepo.findById(saved.id()).orElseThrow();

        // net_* and gross_* are distinct columns of the same embedded type.
        assertEquals(100, found.net().amount());
        assertEquals("EUR", found.net().currency());
        assertEquals(120, found.gross().amount());
        assertEquals("USD", found.gross().currency());
    }

    @Test
    void testEmbeddedWithNullSubValues() {
        InvoiceEntity saved = invoiceRepo.save(new InvoiceEntity(
                null, "INV-" + System.nanoTime(),
                new Audit(null, null),
                new Money(0, "EUR"),
                new Money(0, "EUR")));

        InvoiceEntity found = invoiceRepo.findById(saved.id()).orElseThrow();

        // Embedded is instantiated even when all its columns are null.
        assertNotNull(found.audit());
        assertNull(found.audit().createdAt());
        assertNull(found.audit().createdBy());
    }
}
