package fr.traqueur.victor.repository;

import fr.traqueur.victor.annotations.Query;
import fr.traqueur.victor.entity.Repository;
import fr.traqueur.victor.entity.UserEntity;
import fr.traqueur.victor.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends Repository<UserEntity, User, Long> {
    
    // Test 1: Égalité simple
    Optional<UserEntity> findByUsername(String username);
    
    // Test 2: AND
    Optional<UserEntity> findByUsernameAndEmail(String username, String email);
    
    // Test 3: OR
    List<UserEntity> findByUsernameOrEmail(String username, String email);
    
    // Test 4: GreaterThan
    List<UserEntity> findByAgeGreaterThan(int age);
    
    // Test 5: LessThan
    List<UserEntity> findByAgeLessThan(int age);
    
    // Test 6: Like
    List<UserEntity> findByNameLike(String pattern);
    
    // Test 7: IsNull
    List<UserEntity> findByEmailIsNull();
    
    // Test 8: IsNotNull
    List<UserEntity> findByEmailIsNotNull();
    
    // Test 9: ORDER BY
    List<UserEntity> findByActiveOrderByUsernameAsc(boolean active);
    
    // Test 10: ORDER BY DESC
    List<UserEntity> findByActiveOrderByUsernameDesc(boolean active);
    
    // Test 11: Combinaison complexe
    List<UserEntity> findByActiveAndAgeGreaterThanOrderByNameAsc(boolean active, int age);

    @Query("SELECT * FROM users WHERE age > ? AND active = ?")
    List<UserEntity> findActiveUsersOlderThan(int age, boolean active);

    // Query avec paramètres nommés
    @Query("SELECT * FROM users WHERE username = :username")
    Optional<UserEntity> findByUsernameCustom(String username);

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