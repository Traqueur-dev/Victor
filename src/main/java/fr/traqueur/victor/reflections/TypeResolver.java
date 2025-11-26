package fr.traqueur.victor.reflections;

import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.exceptions.VictorException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class TypeResolver {

    public static <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID> RepositoryTypeInfo<DTO,MODEL,ID> resolveRepositoryTypes(Class<? extends Repository<DTO,MODEL,ID>> repositoryInterface) {
        Type[] genericTypes = getGenericTypes(repositoryInterface, Repository.class);

        if (genericTypes.length >= 3) {
            Class<DTO> dtoClass = (Class<DTO>) genericTypes[0];
            Class<MODEL> modelClass = (Class<MODEL>) genericTypes[1];
            Class<ID> idClass = (Class<ID>) genericTypes[2];
            return new RepositoryTypeInfo<>(dtoClass, modelClass, idClass);
        }

        throw new VictorException("Cannot resolve generic types for repository: " + repositoryInterface +
                ". Make sure your repository extends Repository<DTO, MODEL, ID>");
    }

    public static <DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID, REPO extends Repository<DTO,MODEL,ID>> ServiceTypeInfo<DTO,MODEL,ID, REPO> resolveServiceTypes(Class<? extends Service<MODEL,DTO,ID,REPO>> serviceInterface) {
        Type[] genericTypes = getGenericTypes(serviceInterface, Service.class);

        if (genericTypes.length >= 4) {
            Class<MODEL> modelClass = (Class<MODEL>) genericTypes[0];
            Class<DTO> dtoClass = (Class<DTO>) genericTypes[1];
            Class<ID> idClass = (Class<ID>) genericTypes[2];
            Class<REPO> repositoryClass = (Class<REPO>) genericTypes[3];
            return new ServiceTypeInfo<>(modelClass, dtoClass, idClass, repositoryClass);
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

    public record RepositoryTypeInfo<DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID>(Class<DTO> dtoClass, Class<MODEL> modelClass, Class<ID> idClass) {}

    public record ServiceTypeInfo<DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID, REPO extends Repository<DTO, MODEL, ID>>(Class<MODEL> modelClass, Class<DTO> dtoClass, Class<ID> idClass, Class<REPO> repositoryClass) {}
}