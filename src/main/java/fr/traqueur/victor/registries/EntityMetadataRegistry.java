package fr.traqueur.victor.registries;

import fr.traqueur.victor.entity.metadata.EntityMetadata;

import java.util.concurrent.ConcurrentHashMap;

public final class EntityMetadataRegistry {

    private static final EntityMetadataRegistry INSTANCE = new EntityMetadataRegistry();
    private final ConcurrentHashMap<Class<?>, EntityMetadata> cache = new ConcurrentHashMap<>();

    private EntityMetadataRegistry() {}

    public static EntityMetadataRegistry getInstance() {
        return INSTANCE;
    }

    public EntityMetadata getMetadata(Class<?> entityClass) {
        return cache.computeIfAbsent(entityClass, EntityMetadata::of);
    }

    public void registerMetadata(Class<?> entityClass, EntityMetadata metadata) {
        cache.put(entityClass, metadata);
    }

    public void clearCache() {
        cache.clear();
    }

    public boolean isRegistered(Class<?> entityClass) {
        return cache.containsKey(entityClass);
    }
}
