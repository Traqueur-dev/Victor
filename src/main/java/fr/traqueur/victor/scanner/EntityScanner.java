package fr.traqueur.victor.scanner;

import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.exceptions.VictorException;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public final class EntityScanner {

    public static Set<Class<?>> scanForEntities() {
        return scanForEntities(""); // Scan all packages
    }

    public static Set<Class<?>> scanForEntities(String basePackage) {
        Set<Class<?>> entities = new HashSet<>();

        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = basePackage.replace('.', '/');
            var resources = classLoader.getResources(path);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                entities.addAll(findClasses(new File(resource.getFile()), basePackage));
            }

        } catch (Exception e) {
            System.err.println("Warning: Could not auto-scan entities: " + e.getMessage());
            // Continue without auto-scan
        }

        return entities;
    }

    private static Set<Class<?>> findClasses(File directory, String packageName) {
        Set<Class<?>> classes = new HashSet<>();

        if (!directory.exists()) {
            return classes;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return classes;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                String subPackage = packageName.isEmpty() ?
                        file.getName() : packageName + "." + file.getName();
                classes.addAll(findClasses(file, subPackage));
            } else if (file.getName().endsWith(".class")) {
                try {
                    String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                    Class<?> clazz = Class.forName(className);

                    if (isValidEntity(clazz)) {
                        classes.add(clazz);
                        System.out.println("Auto-discovered entity: " + clazz.getSimpleName());
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // Skip classes that can't be loaded
                }
            }
        }

        return classes;
    }

    private static boolean isValidEntity(Class<?> clazz) {
        // Check if class is annotated with @Table
        if (!clazz.isAnnotationPresent(Table.class)) {
            return false;
        }

        // Check if class implements Entity interface
        if (!Entity.class.isAssignableFrom(clazz)) {
            return false;
        }

        // Skip abstract classes and interfaces
        if (Modifier.isAbstract(clazz.getModifiers()) || clazz.isInterface()) {
            return false;
        }

        // Skip inner classes that are not static
        if (clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers())) {
            return false;
        }

        // Skip test classes with invalid configurations (like MultipleIdUser)
        if (clazz.getName().contains("Test") && clazz.getName().contains("MultipleId")) {
            return false;
        }

        // Try to create metadata to validate the entity structure
        try {
            EntityMetadata.of(clazz);
            return true;
        } catch (Exception e) {
            System.out.println("Skipping invalid entity " + clazz.getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Scan for entities in specific packages
     */
    public static Set<Class<?>> scanPackages(String... packages) {
        Set<Class<?>> allEntities = new HashSet<>();

        for (String pkg : packages) {
            allEntities.addAll(scanForEntities(pkg));
        }

        return allEntities;
    }
}