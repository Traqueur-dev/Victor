package fr.traqueur.victor.proxy;

import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.conversion.VictorConverter;
import fr.traqueur.victor.reflections.TypeResolver;
import fr.traqueur.victor.exceptions.VictorException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

public class ServiceProxyHandler<MODEL extends Entity<ID>, DTO extends Dto<MODEL>, ID>
        implements InvocationHandler {

    private final Class<MODEL> modelClass;
    private final Class<DTO> dtoClass;
    private final Class<ID> idClass;

    @SuppressWarnings("unchecked")
    public ServiceProxyHandler(Class<?> serviceInterface) {
        var typeInfo = TypeResolver.resolveServiceTypes(serviceInterface);
        this.modelClass = (Class<MODEL>) typeInfo.modelClass();
        this.dtoClass = (Class<DTO>) typeInfo.dtoClass();
        this.idClass = (Class<ID>) typeInfo.idClass();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        return switch (methodName) {
            case "save" -> save((MODEL) args[0]);
            case "findById" -> findById((ID) args[0]);
            case "findAll" -> findAll();
            case "update" -> update((ID) args[0], (MODEL) args[1]);
            case "deleteById" -> { deleteById((ID) args[0]); yield null; }
            case "delete" -> { delete((MODEL) args[0]); yield null; }
            case "exists" -> exists((ID) args[0]);
            case "count" -> count();
            case "isValid" -> isValid((MODEL) args[0]);
            case "validateAndSave" -> validateAndSave((MODEL) args[0]);
            case "saveAll" -> saveAll((List<MODEL>) args[0]);
            case "deleteAll" -> { deleteAll((List<ID>) args[0]); yield null; }
            case "repository" -> repository();
            default -> throw new VictorException("Unsupported service method: " + methodName);
        };
    }

    private MODEL save(MODEL model) {
        // Validation
        model.beforeSave();
        if (!isValid(model)) {
            throw new VictorException("Invalid model: " + model);
        }

        // TODO: Implement actual save logic
        System.out.println("Service: Saving model: " + model);

        model.afterSave();
        return model;
    }

    private Optional<MODEL> findById(ID id) {
        // TODO: Implement actual findById logic
        System.out.println("Service: Finding model by ID: " + id);
        return Optional.empty();
    }

    private List<MODEL> findAll() {
        // TODO: Implement actual findAll logic
        System.out.println("Service: Finding all models");
        return List.of();
    }

    private MODEL update(ID id, MODEL model) {
        // TODO: Check if exists
        model.setId(id);
        return save(model);
    }

    private void deleteById(ID id) {
        // TODO: Find model for callbacks, then delete
        System.out.println("Service: Deleting model by ID: " + id);
    }

    private void delete(MODEL model) {
        model.beforeDelete();
        System.out.println("Service: Deleting model: " + model);
        model.afterDelete();
    }

    private boolean exists(ID id) {
        // TODO: Implement actual exists logic
        System.out.println("Service: Checking existence for ID: " + id);
        return false;
    }

    private long count() {
        // TODO: Implement actual count logic
        System.out.println("Service: Counting models");
        return 0L;
    }

    private boolean isValid(MODEL model) {
        return model != null && model.isValid();
    }

    private MODEL validateAndSave(MODEL model) {
        if (!isValid(model)) {
            throw new VictorException("Invalid model: " + model);
        }
        return save(model);
    }

    private List<MODEL> saveAll(List<MODEL> models) {
        return models.stream()
                .map(this::save)
                .toList();
    }

    private void deleteAll(List<ID> ids) {
        ids.forEach(this::deleteById);
    }

    private Repository<DTO, MODEL, ID> repository() {
        // TODO: Return actual repository instance when needed
        throw new VictorException("Repository access not yet implemented");
    }

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> serviceInterface) {
        var handler = new ServiceProxyHandler<>(serviceInterface);
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class[]{serviceInterface},
                handler
        );
    }
}