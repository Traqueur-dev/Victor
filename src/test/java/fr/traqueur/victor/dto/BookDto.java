package fr.traqueur.victor.dto;

import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.ManyToOne;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Book;

@Table(table = "books")
public record BookDto(
    @Id Long id,
    @Column(nullable = false, length = 200) String title,
    @ManyToOne(targetDto = AuthorDto.class, nullable = true) AuthorDto author
) implements Dto<Book> {

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