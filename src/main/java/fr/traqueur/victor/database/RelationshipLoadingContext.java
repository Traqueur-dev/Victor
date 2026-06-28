package fr.traqueur.victor.database;

import java.util.HashSet;
import java.util.Set;

/**
 * Thread-local guard that prevents infinite recursion when loading EAGER relationships.
 * <p>
 * When loading User → Orders → User → Orders → ... the second attempt to load User
 * will be detected and short-circuited to null/empty.
 */
public final class RelationshipLoadingContext {

    private static final ThreadLocal<Set<Class<?>>> LOADING_STACK =
            ThreadLocal.withInitial(HashSet::new);

    private RelationshipLoadingContext() {}

    public static boolean isAlreadyLoading(Class<?> entityClass) {
        return LOADING_STACK.get().contains(entityClass);
    }

    public static void push(Class<?> entityClass) {
        LOADING_STACK.get().add(entityClass);
    }

    public static void pop(Class<?> entityClass) {
        Set<Class<?>> stack = LOADING_STACK.get();
        stack.remove(entityClass);
        if (stack.isEmpty()) {
            LOADING_STACK.remove();
        }
    }
}