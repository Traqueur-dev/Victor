package fr.traqueur.victor.test.entities;

import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entities.Entity;

@Table(table = "users")
public class User implements Entity<Long> {
    
    @Id()
    private Long id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String username;
    
    @Column()
    private String email;
    
    @Column
    private Integer age;
    
    @Column
    private Boolean active;
    
    @Column(length = 100)
    private String name;
    
    // Constructeurs
    public User() {}
    
    public User(String username, String email, Integer age, Boolean active, String name) {
        this.username = username;
        this.email = email;
        this.age = age;
        this.active = active;
        this.name = name;
    }
    
    // Getters/Setters
    @Override
    public Long getId() { return id; }
    
    @Override
    public void setId(Long id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', email='" + email + 
               "', age=" + age + ", active=" + active + ", name='" + name + "'}";
    }
}