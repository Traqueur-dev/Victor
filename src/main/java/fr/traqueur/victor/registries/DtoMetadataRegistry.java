package fr.traqueur.victor.registries;

import fr.traqueur.victor.entities.metadata.DtoMetadata;

import java.util.concurrent.ConcurrentHashMap;

public final class DtoMetadataRegistry {

    private static final DtoMetadataRegistry INSTANCE = new DtoMetadataRegistry();
    private final ConcurrentHashMap<Class<?>, DtoMetadata> cache = new ConcurrentHashMap<>();

    private DtoMetadataRegistry() {}

    public static DtoMetadataRegistry getInstance() {
        return INSTANCE;
    }

    public DtoMetadata getMetadata(Class<?> dtoClass) {
        return cache.computeIfAbsent(dtoClass, DtoMetadata::of);
    }

    public void registerMetadata(Class<?> dtoClass, DtoMetadata metadata) {
        cache.put(dtoClass, metadata);
    }

    public void clearCache() {
        cache.clear();
    }

    public boolean isRegistered(Class<?> dtoClass) {
        return cache.containsKey(dtoClass);
    }
}
