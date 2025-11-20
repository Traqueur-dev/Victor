package fr.traqueur.victor.integration;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.annotations.*;
import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.exceptions.VictorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

class VictorIntegrationTest {
    
    @AfterEach
    void cleanup() {
        try {
            if (Victor.getDefault() != null) {
                Victor.getDefault().close();
            }
        } catch (Exception ignored) {}
    }
    
    @Table(table = "users")
    static class User implements Entity<Long> {
        @Id
        private Long id;
        
        @Column(nullable = false, unique = true)
        private String username;
        
        @Column(nullable = false)
        private String email;
        
        public User() {}
        
        @Override
        public Long getId() { return id; }
        @Override
        public void setId(Long id) { this.id = id; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
    
    record UserDto(Long id, String username, String email) implements Dto<User> {
        @Override
        public User toModel() {
            var user = new User();
            user.setId(id);
            user.setUsername(username);
            user.setEmail(email);
            return user;
        }
    }
    
    interface UserRepository extends Repository<UserDto, User, Long> {}
    
    interface UserService extends Service<User, UserDto, Long> {}
    
    @Test
    void testVictorConfiguration() {
        Victor.connect("jdbc:h2:mem:test");
        
        var victor = Victor.getDefault();
        assertNotNull(victor);
        
        var config = victor.getConfiguration();
        assertNotNull(config);
        assertEquals("jdbc:h2:mem:test", config.connectionUrl());
    }
    
    @Test
    void testRepositoryCreation() {
        Victor.connect("jdbc:h2:mem:test");
        
        // Should throw for now since implementation is not done
        assertThrows(VictorException.class, () -> {
            Victor.withRepository(UserRepository.class);
        });
    }
    
    @Test
    void testServiceCreation() {
        Victor.connect("jdbc:h2:mem:test");
        
        // Should throw for now since implementation is not done  
        assertThrows(VictorException.class, () -> {
            Victor.withService(UserService.class);
        });
    }
    
    @Test
    void testGenericWith() {
        Victor.connect("jdbc:h2:mem:test");
        
        // Should throw for now since implementation is not done
        assertThrows(VictorException.class, () -> {
            Victor.with(UserRepository.class);
        });
    }
    
    @Test
    void testMultipleVictorInstances() {
        var victor1 = Victor.h2("test1");
        var victor2 = Victor.h2("test2");
        
        assertNotEquals(victor1, victor2);
        
        victor1.close();
        victor2.close();
    }
    
    @Test
    void testVictorClose() {
        var victor = Victor.sqlite("test.db");
        victor.close();
        
        // Should be able to close multiple times
        assertDoesNotThrow(victor::close);
    }
}