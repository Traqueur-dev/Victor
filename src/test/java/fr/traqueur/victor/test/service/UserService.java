package fr.traqueur.victor.test.service;

import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.test.dto.UserDto;
import fr.traqueur.victor.test.entities.User;
import fr.traqueur.victor.test.repository.UserRepository;

public interface UserService extends Service<User, UserDto, Long, UserRepository> {
}