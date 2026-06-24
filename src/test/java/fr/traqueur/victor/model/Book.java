package fr.traqueur.victor.model;
import fr.traqueur.victor.entity.Model;


public class Book implements Model<Long> {

    private Long id;
    private String title;
    private Long authorId;

    public Book() {}

    public Book(String title, Long authorId) {
        this.title = title;
        this.authorId = authorId;
    }

    @Override
    public Long getId() { return id; }

    @Override
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
}
