package fr.traqueur.victor.repository;

import fr.traqueur.victor.annotations.Query;
import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.dto.UserDto;
import fr.traqueur.victor.entities.User;

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

    @Query("SELECT * FROM users WHERE age > ? AND active = ?")
    List<UserDto> findActiveUsersOlderThan(int age, boolean active);

    // Query avec paramètres nommés
    @Query("SELECT * FROM users WHERE username = :username")
    Optional<UserDto> findByUsernameCustom(String username);

    // Query de comptage
    @Query("SELECT COUNT(*) FROM users WHERE active = :active")
    long countByActive(boolean active);

    // Query d'update
    @Query("UPDATE users SET active = :active WHERE age < :maxAge")
    int updateActiveByAge(boolean active, int maxAge);

    // Query de delete
    @Query("DELETE FROM users WHERE age < :minAge")
    int deleteByAgeLessThan(int minAge);
}