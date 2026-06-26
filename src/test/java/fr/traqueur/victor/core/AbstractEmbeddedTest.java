package fr.traqueur.victor.core;

import fr.traqueur.victor.entity.Audit;
import fr.traqueur.victor.entity.InvoiceEntity;
import fr.traqueur.victor.entity.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractEmbeddedTest extends AbstractVictorTest {

    @Test
    void testEmbeddedPersistedAndMapped() {
        InvoiceEntity saved = invoiceRepo.save(new InvoiceEntity(
                null, "INV-" + System.nanoTime(),
                new Audit("alice", 1),
                new Money(100, "EUR"),
                new Money(120, "USD")));

        assertNotNull(saved.id());

        InvoiceEntity found = invoiceRepo.findById(saved.id()).orElseThrow();

        // Embedded never null on read.
        assertNotNull(found.audit());
        assertEquals("alice", found.audit().createdBy());
        assertEquals(1, found.audit().revision());
    }

    @Test
    void testEmbeddedPrefixDisambiguatesSameType() {
        InvoiceEntity saved = invoiceRepo.save(new InvoiceEntity(
                null, "INV-" + System.nanoTime(),
                new Audit("bob", 2),
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
        assertNull(found.audit().createdBy());
        assertNull(found.audit().revision());
    }
}
