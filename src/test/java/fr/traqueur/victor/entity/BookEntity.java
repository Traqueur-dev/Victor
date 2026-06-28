package fr.traqueur.victor.entity;

import fr.traqueur.victor.annotations.CascadeType;
import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.ManyToOne;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.model.Book;

@Table(table = "books")
public record BookEntity(
    @Id Long id,
    @Column(nullable = false, length = 200) String title,
    @ManyToOne(targetEntity = AuthorEntity.class, nullable = true, cascade = CascadeType.PERSIST) AuthorEntity author
) implements Entity<Book> {

    @Override
    public Book toModel() {
        Book book = new Book();
        book.setId(id);
        book.setTitle(title);
        if (author != null) {
            book.setAuthorId(author.id());
        }
        return book;
    }
}