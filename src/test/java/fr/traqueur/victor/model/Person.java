package fr.traqueur.victor.model;

import fr.traqueur.victor.entity.Model;

public class Person implements Model<Long> {

    private Long id;
    private String name;

    public Person() {}

    public Person(String name) { this.name = name; }

    @Override
    public Long getId() { return id; }

    @Override
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
