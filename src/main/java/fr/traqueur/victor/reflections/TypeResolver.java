package fr.traqueur.victor.reflections;

import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.exceptions.VictorException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class TypeResolver {

    public static RepositoryTypeInfo resolveRepositoryTypes(Class<? extends Repository<?,?,?>> repositoryInterface) {
        Type[] genericTypes = getGenericTypes(repositoryInterface, Repository.class);

        if (genericTypes.length >= 3) {
            Class<?> dtoClass = (Class<?>) genericTypes[0];
            Class<?> modelClass = (Class<?>) genericTypes[1];
            Class<?> idClass = (Class<?>) genericTypes[2];
            return new RepositoryTypeInfo(dtoClass, modelClass, idClass);
        }

        throw new VictorException("Cannot resolve generic types for repository: " + repositoryInterface +
                ". Make sure your repository extends Repository<DTO, MODEL, ID>");
    }

    public static ServiceTypeInfo resolveServiceTypes(Class<? extends Service<?,?,?,?>> serviceInterface) {
        Type[] genericTypes = getGenericTypes(serviceInterface, Service.class);

        if (genericTypes.length >= 4) {
            Class<?> modelClass = (Class<?>) genericTypes[0];
            Class<?> dtoClass = (Class<?>) genericTypes[1];
            Class<?> idClass = (Class<?>) genericTypes[2];
            Class<?> repositoryClass = (Class<?>) genericTypes[3];
            return new ServiceTypeInfo(modelClass, dtoClass, idClass, repositoryClass);
        }

        throw new VictorException("Cannot resolve generic types for service: " + serviceInterface +
                ". Make sure your service extends Service<MODEL, DTO, ID, REPO>");
    }

    private static Type[] getGenericTypes(Class<?> interfaceClass, Class<?> targetInterface) {
        // Check direct generic interfaces
        for (Type genericInterface : interfaceClass.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType paramType) {
                Type rawType = paramType.getRawType();
                if (rawType == targetInterface) {
                    return paramType.getActualTypeArguments();
                }
            }
        }

        // Check superinterfaces recursively
        for (Type genericInterface : interfaceClass.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType paramType) {
                Type rawType = paramType.getRawType();
                if (rawType instanceof Class<?> superInterface) {
                    return getGenericTypes(superInterface, targetInterface);
                }
            }
        }

        // Check implemented interfaces
        for (Class<?> implementedInterface : interfaceClass.getInterfaces()) {
            if (targetInterface.isAssignableFrom(implementedInterface)) {
                return getGenericTypes(implementedInterface, targetInterface);
            }
        }

        return new Type[0];
    }

    public record RepositoryTypeInfo(Class<?> dtoClass, Class<?> modelClass, Class<?> idClass) {}

    public record ServiceTypeInfo(Class<?> modelClass, Class<?> dtoClass, Class<?> idClass, Class<?> repositoryClass) {}
}