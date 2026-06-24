package fr.traqueur.victor.reflections;

import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.entity.Model;
import fr.traqueur.victor.entity.Repository;
import fr.traqueur.victor.entity.Service;
import fr.traqueur.victor.exceptions.VictorException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class TypeResolver {

    public static <E extends Entity<MODEL>, MODEL extends Model<ID>, ID> RepositoryTypeInfo<E,MODEL,ID> resolveRepositoryTypes(Class<? extends Repository<E,MODEL,ID>> repositoryInterface) {
        Type[] genericTypes = getGenericTypes(repositoryInterface, Repository.class);

        if (genericTypes.length >= 3) {
            Class<E> entityClass = (Class<E>) genericTypes[0];
            Class<MODEL> modelClass = (Class<MODEL>) genericTypes[1];
            Class<ID> idClass = (Class<ID>) genericTypes[2];
            return new RepositoryTypeInfo<>(entityClass, modelClass, idClass);
        }

        throw new VictorException("Cannot resolve generic types for repository: " + repositoryInterface +
                ". Make sure your repository extends Repository<E, MODEL, ID>");
    }

    public static <E extends Entity<MODEL>, MODEL extends Model<ID>, ID, REPO extends Repository<E,MODEL,ID>> ServiceTypeInfo<E,MODEL,ID, REPO> resolveServiceTypes(Class<? extends Service<MODEL,E,ID,REPO>> serviceInterface) {
        Type[] genericTypes = getGenericTypes(serviceInterface, Service.class);

        if (genericTypes.length >= 4) {
            Class<MODEL> modelClass = (Class<MODEL>) genericTypes[0];
            Class<E> entityClass = (Class<E>) genericTypes[1];
            Class<ID> idClass = (Class<ID>) genericTypes[2];
            Class<REPO> repositoryClass = (Class<REPO>) genericTypes[3];
            return new ServiceTypeInfo<>(modelClass, entityClass, idClass, repositoryClass);
        }

        throw new VictorException("Cannot resolve generic types for service: " + serviceInterface +
                ". Make sure your service extends Service<MODEL, E, ID, REPO>");
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

    public record RepositoryTypeInfo<E extends Entity<MODEL>, MODEL extends Model<ID>, ID>(Class<E> entityClass, Class<MODEL> modelClass, Class<ID> idClass) {}

    public record ServiceTypeInfo<E extends Entity<MODEL>, MODEL extends Model<ID>, ID, REPO extends Repository<E, MODEL, ID>>(Class<MODEL> modelClass, Class<E> entityClass, Class<ID> idClass, Class<REPO> repositoryClass) {}
}