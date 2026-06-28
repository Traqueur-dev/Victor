package fr.traqueur.victor.model;
import fr.traqueur.victor.entity.Model;


public class UserV2 implements Model<Long> {

    private Long id;
    private String username;
    private String email;
    private Integer age;
    private Boolean active;
    private String name;
    private String phone;
    private String bio;

    public UserV2() {}

    public UserV2(String username, String email, Integer age, Boolean active, String name, String phone, String bio) {
        this.username = username;
        this.email = email;
        this.age = age;
        this.active = active;
        this.name = name;
        this.phone = phone;
        this.bio = bio;
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

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
}
