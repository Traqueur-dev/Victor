package fr.traqueur.victor;

public class VictorTestRunner {
    
    public static void main(String[] args) {
        System.out.println("=== Victor Test Runner ===");
        
        testBasicConfiguration();
        testDialectDetection();
        testBuilderPattern();
        
        System.out.println("=== All tests completed ===");
    }
    
    private static void testBasicConfiguration() {
        System.out.println("\n--- Basic Configuration Test ---");
        
        try {
            var victor = Victor.sqlite("test.db");
            System.out.println("✓ SQLite Victor created");
            
            var config = victor.getConfiguration();
            System.out.println("✓ Configuration: " + config.dialect());
            System.out.println("✓ URL: " + config.connectionUrl());
            
            victor.close();
            System.out.println("✓ Victor closed");
            
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
        }
    }
    
    private static void testDialectDetection() {
        System.out.println("\n--- Dialect Detection Test ---");
        
        try {
            var victor = Victor.configure()
                .url("jdbc:h2:mem:test")
                .autoDetectDialect()
                .build();
            
            var config = victor.getConfiguration();
            System.out.println("✓ Auto-detected: " + config.dialect());
            
            victor.close();
            
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
        }
    }
    
    private static void testBuilderPattern() {
        System.out.println("\n--- Builder Pattern Test ---");
        
        try {
            var victor = Victor.configure()
                .h2()
                .database("testdb")
                .showSql()
                .autoMigrate()
                .property("MODE", "PostgreSQL")
                .build();
            
            var config = victor.getConfiguration();
            System.out.println("✓ Builder created: " + config.dialect());
            System.out.println("✓ Show SQL: " + config.showSql());
            System.out.println("✓ Auto migrate: " + config.autoMigrate());
            
            var props = config.connectionProperties();
            System.out.println("✓ Properties: " + props.size());
            
            victor.close();
            
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
        }
    }
}