package fr.traqueur.victor.entity;

import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.OneToOne;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.model.Passport;

/** Owning side of a one-to-one relationship (holds the FK column person_id). */
@Table(table = "passports")
public record PassportEntity(
    @Id Long id,
    @Column(nullable = false, length = 40) String number,
    @OneToOne(targetEntity = PersonEntity.class) PersonEntity person
) implements Entity<Passport> {

    @Override
    public Passport toModel() {
        Passport pp = new Passport();
        pp.setId(id);
        pp.setNumber(number);
        return pp;
    }

    public static PassportEntity fromModel(Passport pp) {
        return new PassportEntity(pp.getId(), pp.getNumber(), null);
    }
}
