package fr.traqueur.victor.scanner;

import fr.traqueur.victor.annotations.Table;
import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.metadata.DtoMetadata;
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

public final class DtoScanner {

    public static Set<Class<? extends Dto<?>>> scanForDtos() {
        return scanForDtos("", Thread.currentThread().getContextClassLoader());
    }

    public static Set<Class<? extends Dto<?>>> scanForDtos(String basePackage) {
        return scanForDtos(basePackage, Thread.currentThread().getContextClassLoader());
    }

    public static Set<Class<? extends Dto<?>>> scanForDtos(ClassLoader classLoader) {
        return scanForDtos("", classLoader);
    }

    @SuppressWarnings("unchecked")
    public static Set<Class<? extends Dto<?>>> scanForDtos(String basePackage, ClassLoader classLoader) {
        Collection<URL> urls = new HashSet<>();

        if (basePackage.isEmpty()) {
            urls.addAll(ClasspathHelper.forClassLoader(classLoader));
            urls.addAll(ClasspathHelper.forClassLoader(Thread.currentThread().getContextClassLoader()));
            urls.addAll(ClasspathHelper.forClassLoader(DtoScanner.class.getClassLoader()));
            urls.addAll(ClasspathHelper.forJavaClassPath());
        } else {
            urls.addAll(ClasspathHelper.forPackage(basePackage, classLoader));
            urls.addAll(ClasspathHelper.forPackage(basePackage, Thread.currentThread().getContextClassLoader()));
            urls.addAll(ClasspathHelper.forPackage(basePackage, DtoScanner.class.getClassLoader()));
        }

        ConfigurationBuilder config = new ConfigurationBuilder()
                .setUrls(urls)
                .addClassLoaders(classLoader, Thread.currentThread().getContextClassLoader(), DtoScanner.class.getClassLoader())
                .addScanners(Scanners.TypesAnnotated, Scanners.SubTypes);

        Reflections reflections = new Reflections(config);

        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(Table.class);

        return annotatedClasses.stream()
                .filter(Dto.class::isAssignableFrom)
                .filter(DtoScanner::isValidDto)
                .map(clazz -> {
                    VictorLogger.debug("Auto-discovered DTO: " + clazz.getSimpleName());
                    return (Class<? extends Dto<?>>) clazz;
                })
                .collect(Collectors.toSet());
    }

    private static boolean isValidDto(Class<?> clazz) {
        if (Modifier.isAbstract(clazz.getModifiers()) || clazz.isInterface()) {
            return false;
        }
        if (clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers())) {
            return false;
        }
        try {
            DtoMetadata.of(clazz);
            return true;
        } catch (Exception e) {
            VictorLogger.info("Skipping invalid DTO " + clazz.getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    public static Set<Class<? extends Dto<?>>> scanPackages(String... packages) {
        return scanPackages(Thread.currentThread().getContextClassLoader(), packages);
    }

    public static Set<Class<? extends Dto<?>>> scanPackages(ClassLoader classLoader, String... packages) {
        Set<Class<? extends Dto<?>>> allDtos = new HashSet<>();
        for (String pkg : packages) {
            allDtos.addAll(scanForDtos(pkg, classLoader));
        }
        return allDtos;
    }
}