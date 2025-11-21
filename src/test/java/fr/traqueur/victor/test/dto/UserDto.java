package fr.traqueur.victor.test.dto;

import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.test.entities.User;

public record UserDto(
    Long id,
    String username,
    String email,
    Integer age,
    Boolean active,
    String name
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