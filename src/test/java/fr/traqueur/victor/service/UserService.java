package fr.traqueur.victor.service;

import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.dto.UserDto;
import fr.traqueur.victor.entities.User;
import fr.traqueur.victor.repository.UserRepository;

public interface UserService extends Service<User, UserDto, Long, UserRepository> {
}