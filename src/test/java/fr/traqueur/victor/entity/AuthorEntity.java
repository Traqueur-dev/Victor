package fr.traqueur.victor.entity;

import fr.traqueur.victor.annotations.CascadeType;
import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.OneToMany;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.model.Author;

import java.util.List;

@Table(table = "authors")
public record AuthorEntity(
    @Id Long id,
    @Column(nullable = false, length = 100) String name,
    @Column(length = 60) String country,
    @OneToMany(mappedBy = "author", cascade = CascadeType.PERSIST) List<BookEntity> books
) implements Entity<Author> {

    @Override
    public Author toModel() {
        Author author = new Author();
        author.setId(id);
        author.setName(name);
        author.setCountry(country);
        return author;
    }
}