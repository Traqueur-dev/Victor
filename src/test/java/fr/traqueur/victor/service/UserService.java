package fr.traqueur.victor.service;

import fr.traqueur.victor.entity.Service;
import fr.traqueur.victor.entity.UserEntity;
import fr.traqueur.victor.model.User;
import fr.traqueur.victor.repository.UserRepository;

public interface UserService extends Service<User, UserEntity, Long, UserRepository> {
}