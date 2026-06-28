package fr.traqueur.victor.model;
import fr.traqueur.victor.entity.Model;

public class Course implements Model<Long> {

    private Long id;

    public Course() {}

    @Override
    public Long getId() { return id; }

    @Override
    public void setId(Long id) { this.id = id; }
}
