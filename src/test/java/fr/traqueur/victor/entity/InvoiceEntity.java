package fr.traqueur.victor.entity;

import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Embedded;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.model.Invoice;

/**
 * Record entity exercising @Embedded: a single Audit (no prefix) plus the same
 * Money type embedded twice with distinct prefixes.
 *
 * <p>Columns: id, number, created_at, created_by, net_amount, net_currency,
 * gross_amount, gross_currency.</p>
 */
@Table(table = "invoices")
public record InvoiceEntity(
    @Id Long id,
    @Column String number,
    @Embedded Audit audit,
    @Embedded(prefix = "net_") Money net,
    @Embedded(prefix = "gross_") Money gross
) implements Entity<Invoice> {

    @Override
    public Invoice toModel() {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setNumber(number);
        invoice.setAudit(audit);
        invoice.setNet(net);
        invoice.setGross(gross);
        return invoice;
    }

    public static InvoiceEntity fromModel(Invoice invoice) {
        return new InvoiceEntity(
            invoice.getId(),
            invoice.getNumber(),
            invoice.getAudit(),
            invoice.getNet(),
            invoice.getGross()
        );
    }
}
