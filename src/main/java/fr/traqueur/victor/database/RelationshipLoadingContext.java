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

    public static boolean isAlreadyLoading(Class<?> dtoClass) {
        return LOADING_STACK.get().contains(dtoClass);
    }

    public static void push(Class<?> dtoClass) {
        LOADING_STACK.get().add(dtoClass);
    }

    public static void pop(Class<?> dtoClass) {
        Set<Class<?>> stack = LOADING_STACK.get();
        stack.remove(dtoClass);
        if (stack.isEmpty()) {
            LOADING_STACK.remove();
        }
    }
}