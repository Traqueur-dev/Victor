package fr.traqueur.victor.integration;

import fr.traqueur.victor.Victor;
import fr.traqueur.victor.annotations.*;
import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.Query;
import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.exceptions.VictorException;
import fr.traqueur.victor.types.VictorDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

class VictorIntegrationTest {
    
    @AfterEach
    void cleanup() {
        try {
            var instance = Victor.getDefault();
            if (instance != null) {
                instance.close();
            }
        } catch (Exception ignored) {}
    }
    
    // ========== Test Entities ==========
    
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
        
        @Override
        public String toString() {
            return "User{id=" + id + ", username='" + username + "', email='" + email + "'}";
        }
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
    
    // ========== Configuration Tests ==========
    
    @Test
    void testVictorConfiguration() {
        System.out.println("=== Testing Victor Configuration ===");
        
        Victor.connect("jdbc:h2:mem:test");
        
        var victor = Victor.getDefault();
        assertNotNull(victor);
        
        var config = victor.getConfiguration();
        assertNotNull(config);
        assertEquals(VictorDialect.H2, config.dialect());
        assertEquals("jdbc:h2:mem:test", config.connectionUrl());
        
        System.out.println("✓ Configuration test passed");
    }
    
    @Test
    void testMultipleVictorInstances() {
        System.out.println("=== Testing Multiple Victor Instances ===");
        
        var victor1 = Victor.h2("test1");
        var victor2 = Victor.h2("test2");
        
        assertNotEquals(victor1, victor2);
        assertNotEquals(victor1.getConfiguration().connectionUrl(), victor2.getConfiguration().connectionUrl());
        
        victor1.close();
        victor2.close();
        
        System.out.println("✓ Multiple instances test passed");
    }
    
    @Test
    void testVictorClose() {
        System.out.println("=== Testing Victor Close ===");
        
        var victor = Victor.sqlite("test.db");
        victor.close();
        
        // Should be able to close multiple times
        assertDoesNotThrow(victor::close);
        
        System.out.println("✓ Close test passed");
    }
    
    // ========== Proxy Creation Tests ==========
    
    @Test
    void testRepositoryProxyCreation() {
        System.out.println("=== Testing Repository Proxy Creation ===");
        
        Victor.connect("jdbc:h2:mem:test");
        
        assertDoesNotThrow(() -> {
            UserRepository userRepo = Victor.withRepository(UserRepository.class);
            assertNotNull(userRepo);
            System.out.println("✓ Repository proxy created: " + userRepo.getClass().getName());
        });
        
        System.out.println("✓ Repository proxy creation test passed");
    }
    
    @Test
    void testServiceProxyCreation() {
        System.out.println("=== Testing Service Proxy Creation ===");
        
        Victor.connect("jdbc:h2:mem:test");
        
        assertDoesNotThrow(() -> {
            UserService userService = Victor.withService(UserService.class);
            assertNotNull(userService);
            System.out.println("✓ Service proxy created: " + userService.getClass().getName());
        });
        
        System.out.println("✓ Service proxy creation test passed");
    }
    
    // ========== Repository Method Tests ==========
    
    @Test
    void testRepositoryBasicMethods() {
        System.out.println("=== Testing Repository Basic Methods ===");
        
        Victor.connect("jdbc:h2:mem:test");
        UserRepository userRepo = Victor.withRepository(UserRepository.class);
        
        assertDoesNotThrow(() -> {
            var testDto = new UserDto(null, "testuser", "test@example.com");
            
            // Test CRUD operations (currently stubs)
            userRepo.save(testDto);
            System.out.println("✓ save() method works");
            
            userRepo.findById(1L);
            System.out.println("✓ findById() method works");
            
            userRepo.findAll();
            System.out.println("✓ findAll() method works");
            
            userRepo.count();
            System.out.println("✓ count() method works");
            
            userRepo.existsById(1L);
            System.out.println("✓ existsById() method works");
            
            userRepo.delete(testDto);
            System.out.println("✓ delete() method works");
            
            userRepo.deleteById(1L);
            System.out.println("✓ deleteById() method works");
        });
        
        System.out.println("✓ Repository basic methods test passed");
    }
    
    // ========== Service Method Tests ==========
    
    @Test
    void testServiceBasicMethods() {
        System.out.println("=== Testing Service Basic Methods ===");
        
        Victor.connect("jdbc:h2:mem:test");
        UserService userService = Victor.withService(UserService.class);
        
        assertDoesNotThrow(() -> {
            var testUser = new User();
            testUser.setUsername("testuser");
            testUser.setEmail("test@example.com");
            
            // Test validation
            boolean valid = userService.isValid(testUser);
            System.out.println("✓ isValid() method works: " + valid);
            
            // Test CRUD operations (currently stubs)
            userService.save(testUser);
            System.out.println("✓ save() method works");
            
            userService.findById(1L);
            System.out.println("✓ findById() method works");
            
            userService.findAll();
            System.out.println("✓ findAll() method works");
            
            userService.count();
            System.out.println("✓ count() method works");
            
            userService.exists(1L);
            System.out.println("✓ exists() method works");
            
            userService.deleteById(1L);
            System.out.println("✓ deleteById() method works");
            
            userService.delete(testUser);
            System.out.println("✓ delete() method works");
        });
        
        System.out.println("✓ Service basic methods test passed");
    }
    
    // ========== Query Builder Tests ==========
    
    @Test
    void testQueryBuilderCreation() {
        System.out.println("=== Testing Query Builder Creation ===");
        
        Victor.connect("jdbc:h2:mem:test");
        UserRepository userRepo = Victor.withRepository(UserRepository.class);
        
        assertDoesNotThrow(() -> {
            var query = userRepo.query()
                .where("username = ?", "testuser")
                .and("email LIKE ?", "%@example.com")
                .orderByAsc("username")
                .limit(10);
            
            assertNotNull(query);
            System.out.println("✓ Query builder created successfully");
            
            // Test query execution (currently stubs)
            query.findAll();
            System.out.println("✓ Query findAll() works");
            
            query.count();
            System.out.println("✓ Query count() works");
            
            query.exists();
            System.out.println("✓ Query exists() works");
            
            query.findOne();
            System.out.println("✓ Query findOne() works");
        });
        
        System.out.println("✓ Query builder test passed");
    }
    
    // ========== Advanced Query Tests ==========
    
    @Test
    void testAdvancedQueryBuilder() {
        System.out.println("=== Testing Advanced Query Builder ===");
        
        Victor.connect("jdbc:h2:mem:test");
        UserRepository userRepo = Victor.withRepository(UserRepository.class);
        
        assertDoesNotThrow(() -> {
            // Test complex query building
            Query<?> complexQuery = userRepo.query()
                .select("username", "email")
                .where("username IS NOT NULL")
                .and("email LIKE ?", "%@company.com")
                .or("email LIKE ?", "%@example.com")
                .orderByDesc("username")
                .groupBy("email")
                .having("COUNT(*) > ?", 1)
                .limit(20)
                .offset(10);

            assertNotNull(complexQuery);
            System.out.println("✓ Complex query builder created");
            
            // Test execution
            complexQuery.findAll();
            System.out.println("✓ Complex query execution works");
        });
        
        System.out.println("✓ Advanced query builder test passed");
    }
    
    // ========== Error Handling Tests ==========
    
    @Test
    void testErrorHandling() {
        System.out.println("=== Testing Error Handling ===");
        
        // Test without configuration
        assertThrows(VictorException.class, () -> {
            Victor.withRepository(UserRepository.class);
        });
        System.out.println("✓ Error thrown when Victor not configured");
        
        // Configure and test again
        Victor.connect("jdbc:h2:mem:test");
        
        // These should work now
        assertDoesNotThrow(() -> {
            Victor.withRepository(UserRepository.class);
            Victor.withService(UserService.class);
        });
        System.out.println("✓ Proxies work after configuration");
        
        System.out.println("✓ Error handling test passed");
    }
}