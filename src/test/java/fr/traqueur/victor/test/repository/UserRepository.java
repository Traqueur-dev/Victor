package fr.traqueur.victor.test.repository;

import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.test.dto.UserDto;
import fr.traqueur.victor.test.entities.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends Repository<UserDto, User, Long> {
    
    // Test 1: Égalité simple
    Optional<UserDto> findByUsername(String username);
    
    // Test 2: AND
    Optional<UserDto> findByUsernameAndEmail(String username, String email);
    
    // Test 3: OR
    List<UserDto> findByUsernameOrEmail(String username, String email);
    
    // Test 4: GreaterThan
    List<UserDto> findByAgeGreaterThan(int age);
    
    // Test 5: LessThan
    List<UserDto> findByAgeLessThan(int age);
    
    // Test 6: Like
    List<UserDto> findByNameLike(String pattern);
    
    // Test 7: IsNull
    List<UserDto> findByEmailIsNull();
    
    // Test 8: IsNotNull
    List<UserDto> findByEmailIsNotNull();
    
    // Test 9: ORDER BY
    List<UserDto> findByActiveOrderByUsernameAsc(boolean active);
    
    // Test 10: ORDER BY DESC
    List<UserDto> findByActiveOrderByUsernameDesc(boolean active);
    
    // Test 11: Combinaison complexe
    List<UserDto> findByActiveAndAgeGreaterThanOrderByNameAsc(boolean active, int age);
}