package fr.traqueur.victor.entities;

public class Student implements Entity<Long> {

    private Long id;

    public Student() {}

    @Override
    public Long getId() { return id; }

    @Override
    public void setId(Long id) { this.id = id; }
}