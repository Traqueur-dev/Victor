package fr.traqueur.victor.entities;

import fr.traqueur.victor.entities.Entity;

public class Author implements Entity<Long> {

    private Long id;
    private String name;
    private String country;

    public Author() {}

    public Author(String name, String country) {
        this.name = name;
        this.country = country;
    }

    @Override
    public Long getId() { return id; }

    @Override
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}