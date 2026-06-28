package fr.traqueur.victor.entity;

import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.OneToOne;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.model.Person;

/** Inverse side of a one-to-one relationship (no FK column; mappedBy the owning side). */
@Table(table = "persons")
public record PersonEntity(
    @Id Long id,
    @Column(nullable = false, length = 100) String name,
    @OneToOne(mappedBy = "person") PassportEntity passport
) implements Entity<Person> {

    @Override
    public Person toModel() {
        Person p = new Person();
        p.setId(id);
        p.setName(name);
        return p;
    }

    public static PersonEntity fromModel(Person p) {
        return new PersonEntity(p.getId(), p.getName(), null);
    }
}
