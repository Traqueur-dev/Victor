package fr.traqueur.victor.model;

import fr.traqueur.victor.entity.Audit;
import fr.traqueur.victor.entity.Money;
import fr.traqueur.victor.entity.Model;

public class Invoice implements Model<Long> {

    private Long id;
    private String number;
    private Audit audit;
    private Money net;
    private Money gross;

    public Invoice() {}

    @Override
    public Long getId() { return id; }

    @Override
    public void setId(Long id) { this.id = id; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public Audit getAudit() { return audit; }
    public void setAudit(Audit audit) { this.audit = audit; }

    public Money getNet() { return net; }
    public void setNet(Money net) { this.net = net; }

    public Money getGross() { return gross; }
    public void setGross(Money gross) { this.gross = gross; }
}
