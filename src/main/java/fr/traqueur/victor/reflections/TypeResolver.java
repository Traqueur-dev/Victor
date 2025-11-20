package fr.traqueur.victor.reflections;

import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.exceptions.VictorException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class TypeResolver {

    public static RepositoryTypeInfo resolveRepositoryTypes(Class<?> repositoryInterface) {
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

    public static ServiceTypeInfo resolveServiceTypes(Class<?> serviceInterface) {
        Type[] genericTypes = getGenericTypes(serviceInterface, Service.class);

        if (genericTypes.length >= 3) {
            Class<?> modelClass = (Class<?>) genericTypes[0];
            Class<?> dtoClass = (Class<?>) genericTypes[1];
            Class<?> idClass = (Class<?>) genericTypes[2];
            return new ServiceTypeInfo(modelClass, dtoClass, idClass);
        }

        throw new VictorException("Cannot resolve generic types for service: " + serviceInterface +
                ". Make sure your service extends Service<MODEL, DTO, ID>");
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
                    try {
                        return getGenericTypes(superInterface, targetInterface);
                    } catch (VictorException ignored) {
                        // Continue searching in other interfaces
                    }
                }
            }
        }

        // Check implemented interfaces
        for (Class<?> implementedInterface : interfaceClass.getInterfaces()) {
            if (targetInterface.isAssignableFrom(implementedInterface)) {
                try {
                    return getGenericTypes(implementedInterface, targetInterface);
                } catch (VictorException ignored) {
                    // Continue searching
                }
            }
        }

        return new Type[0];
    }

    public static boolean isDtoClass(Class<?> clazz) {
        return Dto.class.isAssignableFrom(clazz);
    }

    public static boolean isEntityClass(Class<?> clazz) {
        return Entity.class.isAssignableFrom(clazz);
    }

    public static Class<?> extractIdType(Class<? extends Entity<?>> entityClass) {
        Type[] interfaces = entityClass.getGenericInterfaces();
        for (Type intf : interfaces) {
            if (intf instanceof ParameterizedType paramType) {
                if (paramType.getRawType() == Entity.class) {
                    return (Class<?>) paramType.getActualTypeArguments()[0];
                }
            }
        }
        return Object.class;
    }

    public record RepositoryTypeInfo(Class<?> dtoClass, Class<?> modelClass, Class<?> idClass) {}

    public record ServiceTypeInfo(Class<?> modelClass, Class<?> dtoClass, Class<?> idClass) {}
}