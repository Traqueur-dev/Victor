package fr.traqueur.victor.entity;

import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.model.UserV2;

@Table(table = "users")
public record UserV2Entity(
    @Id Long id,
    @Column(nullable = false, unique = true, length = 50) String username,
    @Column String email,
    @Column Integer age,
    @Column Boolean active,
    @Column(length = 100) String name,
    @Column(length = 20) String phone,
    @Column String bio
) implements Entity<UserV2> {

    @Override
    public UserV2 toModel() {
        UserV2 user = new UserV2();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setAge(age);
        user.setActive(active);
        user.setName(name);
        user.setPhone(phone);
        user.setBio(bio);
        return user;
    }
}