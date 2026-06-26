package fr.traqueur.victor.entity;

import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.model.Vehicle;

/**
 * Class-based entity (not a record) extending {@link AuditableEntity}, so that
 * the {@code created_by} column is declared on a superclass. Exercises
 * EntityMapper.mapToClass hierarchy walk.
 */
@Table(table = "vehicles")
public class VehicleEntity extends AuditableEntity implements Entity<Vehicle> {

    @Id
    private Long id;

    @Column(length = 100)
    private String brand;

    public VehicleEntity() {}

    public VehicleEntity(Long id, String brand, String createdBy) {
        this.id = id;
        this.brand = brand;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public String getBrand() { return brand; }

    @Override
    public Vehicle toModel() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setBrand(brand);
        vehicle.setCreatedBy(createdBy);
        return vehicle;
    }

    public static VehicleEntity fromModel(Vehicle vehicle) {
        return new VehicleEntity(vehicle.getId(), vehicle.getBrand(), vehicle.getCreatedBy());
    }
}
