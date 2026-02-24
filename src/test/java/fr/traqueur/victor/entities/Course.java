package fr.traqueur.victor.entities;

public class Course implements Entity<Long> {

    private Long id;

    public Course() {}

    @Override
    public Long getId() { return id; }

    @Override
    public void setId(Long id) { this.id = id; }
}