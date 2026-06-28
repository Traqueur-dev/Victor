package fr.traqueur.victor.model;

import fr.traqueur.victor.entity.Model;

public class Passport implements Model<Long> {

    private Long id;
    private String number;

    public Passport() {}

    public Passport(String number) { this.number = number; }

    @Override
    public Long getId() { return id; }

    @Override
    public void setId(Long id) { this.id = id; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
}
