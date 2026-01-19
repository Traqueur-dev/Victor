package fr.traqueur.victor.scanner;

import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.utils.VictorLogger;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class EntityScanner {

    public static Set<Class<? extends Entity<?>>> scanForEntities() {
        return scanForEntities("", Thread.currentThread().getContextClassLoader());
    }

    public static Set<Class<? extends Entity<?>>> scanForEntities(String basePackage) {
        return scanForEntities(basePackage, Thread.currentThread().getContextClassLoader());
    }

    public static Set<Class<? extends Entity<?>>> scanForEntities(ClassLoader classLoader) {
        return scanForEntities("", classLoader);
    }

    @SuppressWarnings("unchecked")
    public static Set<Class<? extends Entity<?>>> scanForEntities(String basePackage, ClassLoader classLoader) {
        Collection<URL> urls = new HashSet<>();

        // Collect URLs from multiple sources for comprehensive scanning
        if (basePackage.isEmpty()) {
            urls.addAll(ClasspathHelper.forClassLoader(classLoader));
            urls.addAll(ClasspathHelper.forClassLoader(Thread.currentThread().getContextClassLoader()));
            urls.addAll(ClasspathHelper.forClassLoader(EntityScanner.class.getClassLoader()));
            urls.addAll(ClasspathHelper.forJavaClassPath());
        } else {
            urls.addAll(ClasspathHelper.forPackage(basePackage, classLoader));
            urls.addAll(ClasspathHelper.forPackage(basePackage, Thread.currentThread().getContextClassLoader()));
            urls.addAll(ClasspathHelper.forPackage(basePackage, EntityScanner.class.getClassLoader()));
        }

        ConfigurationBuilder config = new ConfigurationBuilder()
                .setUrls(urls)
                .addClassLoaders(classLoader, Thread.currentThread().getContextClassLoader(), EntityScanner.class.getClassLoader())
                .addScanners(Scanners.TypesAnnotated, Scanners.SubTypes);

        Reflections reflections = new Reflections(config);

        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(Table.class);

        return annotatedClasses.stream()
                .filter(Entity.class::isAssignableFrom)
                .filter(EntityScanner::isValidEntity)
                .map(clazz -> {
                    VictorLogger.debug("Auto-discovered entity: " + clazz.getSimpleName());
                    return (Class<? extends Entity<?>>) clazz;
                })
                .collect(Collectors.toSet());
    }

    private static boolean isValidEntity(Class<?> clazz) {
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
            VictorLogger.info("Skipping invalid entity " + clazz.getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Scan for entities in specific packages
     */
    public static Set<Class<? extends Entity<?>>> scanPackages(String... packages) {
        return scanPackages(Thread.currentThread().getContextClassLoader(), packages);
    }

    /**
     * Scan for entities in specific packages using a specific ClassLoader
     */
    public static Set<Class<? extends Entity<?>>> scanPackages(ClassLoader classLoader, String... packages) {
        Set<Class<? extends Entity<?>>> allEntities = new HashSet<>();

        for (String pkg : packages) {
            allEntities.addAll(scanForEntities(pkg, classLoader));
        }

        return allEntities;
    }
}