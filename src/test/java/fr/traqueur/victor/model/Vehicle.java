package fr.traqueur.victor.model;

import fr.traqueur.victor.entity.Model;

public class Vehicle implements Model<Long> {

    private Long id;
    private String brand;
    private String createdBy;

    public Vehicle() {}

    public Vehicle(String brand, String createdBy) {
        this.brand = brand;
        this.createdBy = createdBy;
    }

    @Override
    public Long getId() { return id; }

    @Override
    public void setId(Long id) { this.id = id; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
