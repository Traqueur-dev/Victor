package fr.traqueur.victor.dto;

import fr.traqueur.victor.annotations.Column;
import fr.traqueur.victor.annotations.Id;
import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.User;

@Table(table = "users")
public record UserDto(
    @Id Long id,
    @Column(nullable = false, unique = true, length = 50) String username,
    @Column String email,
    @Column Integer age,
    @Column Boolean active,
    @Column(length = 100) String name
) implements Dto<User> {

    @Override
    public User toModel() {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setAge(age);
        user.setActive(active);
        user.setName(name);
        return user;
    }

    public static UserDto fromModel(User user) {
        return new UserDto(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getAge(),
            user.getActive(),
            user.getName()
        );
    }
}