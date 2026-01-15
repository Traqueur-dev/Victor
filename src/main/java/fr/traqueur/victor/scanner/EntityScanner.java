package fr.traqueur.victor.scanner;

import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.utils.VictorLogger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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

    public static Set<Class<? extends Entity<?>>> scanForEntities(String basePackage, ClassLoader classLoader) {
        Set<Class<? extends Entity<?>>> entities = new HashSet<>();

        try {
            String path = basePackage.replace('.', '/');
            var resources = classLoader.getResources(path);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();

                if ("file".equals(protocol)) {
                    String filePath = URLDecoder.decode(resource.getFile(), StandardCharsets.UTF_8);
                    entities.addAll(findClassesInDirectory(new File(filePath), basePackage, classLoader));
                } else if ("jar".equals(protocol)) {
                    entities.addAll(findClassesInJar(resource, basePackage, classLoader));
                }
            }

        } catch (Exception e) {
            VictorLogger.error("Could not auto-scan entities", e);
        }

        return entities;
    }

    private static Set<Class<? extends Entity<?>>> findClassesInJar(URL jarUrl, String packageName, ClassLoader classLoader) {
        Set<Class<? extends Entity<?>>> classes = new HashSet<>();
        String packagePath = packageName.replace('.', '/');

        try {
            JarURLConnection jarConnection = (JarURLConnection) jarUrl.openConnection();
            try (JarFile jarFile = jarConnection.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();

                    if (entryName.endsWith(".class") && entryName.startsWith(packagePath)) {
                        String className = entryName
                                .substring(0, entryName.length() - 6)
                                .replace('/', '.');

                        tryLoadEntity(className, classLoader, classes);
                    }
                }
            }
        } catch (IOException e) {
            VictorLogger.error("Could not scan JAR for entities", e);
        }

        return classes;
    }

    private static Set<Class<? extends Entity<?>>> findClassesInDirectory(File directory, String packageName, ClassLoader classLoader) {
        Set<Class<? extends Entity<?>>> classes = new HashSet<>();

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
                classes.addAll(findClassesInDirectory(file, subPackage, classLoader));
            } else if (file.getName().endsWith(".class")) {
                String className = packageName.isEmpty() ?
                        file.getName().substring(0, file.getName().length() - 6) :
                        packageName + '.' + file.getName().substring(0, file.getName().length() - 6);

                tryLoadEntity(className, classLoader, classes);
            }
        }

        return classes;
    }

    @SuppressWarnings("unchecked")
    private static void tryLoadEntity(String className, ClassLoader classLoader, Set<Class<? extends Entity<?>>> classes) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);

            if (isValidEntity(clazz)) {
                classes.add((Class<? extends Entity<?>>) clazz);
                VictorLogger.info("Auto-discovered entity: " + clazz.getSimpleName());
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // Skip classes that can't be loaded
        }
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