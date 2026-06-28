package fr.traqueur.victor.model;
import fr.traqueur.victor.entity.Model;


public class User implements Model<Long> {

    private Long id;
    private String username;
    private String email;
    private Integer age;
    private Boolean active;
    private String name;

    public User() {}

    public User(String username, String email, Integer age, Boolean active, String name) {
        this.username = username;
        this.email = email;
        this.age = age;
        this.active = active;
        this.name = name;
    }

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
