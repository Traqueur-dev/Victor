package fr.traqueur.victor.proxy;

import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.entity.Model;
import fr.traqueur.victor.entity.Repository;
import fr.traqueur.victor.conversion.VictorConverter;
import fr.traqueur.victor.entity.Service;
import fr.traqueur.victor.reflections.TypeResolver;
import fr.traqueur.victor.database.SqlExecutor;
import fr.traqueur.victor.entity.dialect.Dialect;
import fr.traqueur.victor.exceptions.VictorException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class ServiceProxyHandler<MODEL extends Model<ID>, E extends Entity<MODEL>, ID, REPO extends Repository<E, MODEL, ID>>
        implements InvocationHandler {

    private final Class<MODEL> modelClass;
    private final Class<E> entityClass;
    private final Class<REPO> repositoryInterface;
    private final REPO repository;

    public ServiceProxyHandler(Class<? extends Service<MODEL,E,ID,REPO>> serviceInterface, SqlExecutor sqlExecutor, Dialect dialect) {
        var typeInfo = TypeResolver.resolveServiceTypes(serviceInterface);
        this.modelClass = typeInfo.modelClass();
        this.entityClass = typeInfo.entityClass();
        // A service converts models to entities, so the entity must declare fromModel — fail fast here.
        VictorConverter.assertConvertible(this.entityClass);
        this.repositoryInterface = typeInfo.repositoryClass();
        this.repository = RepositoryProxyHandler.createProxy(
                repositoryInterface,
                sqlExecutor,
                dialect);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, method, args);
        }

        return switch (method.getName()) {
            case "save" -> save((MODEL) args[0]);
            case "findById" -> findById((ID) args[0]);
            case "findAll" -> findAll();
            case "update" -> update((ID) args[0], (MODEL) args[1]);
            case "deleteById" -> { deleteById((ID) args[0]); yield null; }
            case "delete" -> { delete((MODEL) args[0]); yield null; }
            case "exists" -> exists((ID) args[0]);
            case "count" -> count();
            case "saveAll" -> saveAll((List<MODEL>) args[0]);
            case "deleteAll" -> { deleteAll((List<ID>) args[0]); yield null; }
            case "repository" -> repository();
            // Any other method: user-defined default stays on the interface,
            // everything else is delegated to the matching repository method.
            default -> method.isDefault()
                    ? InvocationHandler.invokeDefault(proxy, method, args)
                    : delegateToRepository(method, args);
        };
    }

    private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "ServiceProxy[" + modelClass.getSimpleName() + "]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new VictorException("Unsupported Object method: " + method.getName());
        };
    }

    private MODEL save(MODEL model) {
        model.beforeSave();
        if (!isValid(model)) {
            throw new VictorException("Invalid model: " + model);
        }

        try {
            E entity = VictorConverter.modelToEntity(model, entityClass);

            E savedEntity = repository().save(entity);
            MODEL savedModel = savedEntity.toModel();

            savedModel.afterSave();
            return savedModel;

        } catch (Exception e) {
            throw new VictorException("Failed to save model: " + model, e);
        }
    }

    private Optional<MODEL> findById(ID id) {
        try {
            Optional<E> entityOptional = repository().findById(id);
            return entityOptional.map(Entity::toModel);
        } catch (Exception e) {
            throw new VictorException("Failed to find model by ID: " + id, e);
        }
    }

    private List<MODEL> findAll() {
        try {
            List<E> entities = repository().findAll();
            return entities.stream()
                    .map(Entity::toModel)
                    .toList();
        } catch (Exception e) {
            throw new VictorException("Failed to find all models", e);
        }
    }

    private MODEL update(ID id, MODEL model) {
        if (!exists(id)) {
            throw new VictorException("Model not found for update: " + id);
        }

        model.setId(id);
        return save(model);
    }

    private void deleteById(ID id) {
        try {
            Optional<MODEL> modelOptional = findById(id);

            modelOptional.ifPresent(MODEL::beforeDelete);

            // Delete via repository
            repository().deleteById(id);

            modelOptional.ifPresent(MODEL::afterDelete);

        } catch (Exception e) {
            throw new VictorException("Failed to delete model by ID: " + id, e);
        }
    }

    private void delete(MODEL model) {
        model.beforeDelete();

        if (model.getId() == null) {
            throw new VictorException("Cannot delete model without ID");
        }

        try {
            E entity = VictorConverter.modelToEntity(model, entityClass);
            repository().delete(entity);

            model.afterDelete();
        } catch (Exception e) {
            throw new VictorException("Failed to delete model: " + model, e);
        }
    }

    private boolean exists(ID id) {
        try {
            return repository().existsById(id);
        } catch (Exception e) {
            throw new VictorException("Failed to check existence for ID: " + id, e);
        }
    }

    private long count() {
        try {
            return repository().count();
        } catch (Exception e) {
            throw new VictorException("Failed to count models", e);
        }
    }

    private boolean isValid(MODEL model) {
        return model != null && model.isValid();
    }

    private List<MODEL> saveAll(List<MODEL> models) {
        return models.stream()
                .map(this::save)
                .toList();
    }

    private void deleteAll(List<ID> ids) {
        ids.forEach(this::deleteById);
    }

    private REPO repository() {
        return repository;
    }

    // ===================== Generic delegation =====================
    // Custom service methods (dynamic finders, @Query, ...) declared on the
    // service interface are forwarded to the repository method of the same
    // signature, converting MODEL arguments to entities and entity results
    // back to models. This makes the service a true superset of the repository.

    private Object delegateToRepository(Method serviceMethod, Object[] args) {
        Method repositoryMethod = findRepositoryMethod(serviceMethod);
        Object[] entityArgs = mapArgsToEntity(args);
        Object result;
        try {
            result = repositoryMethod.invoke(repository, entityArgs);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new VictorException("Failed to delegate service method '" + serviceMethod.getName()
                    + "' to repository", cause != null ? cause : e);
        } catch (IllegalAccessException e) {
            throw new VictorException("Cannot access repository method: " + serviceMethod.getName(), e);
        }
        return mapResultToModel(result);
    }

    private Method findRepositoryMethod(Method serviceMethod) {
        String name = serviceMethod.getName();
        Class<?>[] serviceParams = serviceMethod.getParameterTypes();
        Method nameMatch = null;
        for (Method candidate : repositoryInterface.getMethods()) {
            if (!candidate.getName().equals(name) || candidate.getParameterCount() != serviceParams.length) {
                continue;
            }
            if (parametersCompatible(serviceParams, candidate.getParameterTypes())) {
                return candidate;
            }
            nameMatch = candidate;
        }
        if (nameMatch != null) {
            return nameMatch;
        }
        throw new VictorException("No repository method matches service method '" + name
                + "'. Declare it on " + repositoryInterface.getSimpleName() + " as well.");
    }

    /** A MODEL parameter on the service maps to an entity parameter on the repository. */
    private boolean parametersCompatible(Class<?>[] serviceParams, Class<?>[] repositoryParams) {
        for (int i = 0; i < serviceParams.length; i++) {
            Class<?> serviceParam = serviceParams[i];
            Class<?> repositoryParam = repositoryParams[i];
            if (repositoryParam.equals(serviceParam)) continue;
            if (serviceParam.equals(modelClass) && repositoryParam.equals(entityClass)) continue;
            if (repositoryParam.isAssignableFrom(serviceParam)) continue;
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private Object[] mapArgsToEntity(Object[] args) {
        if (args == null) return null;
        Object[] mapped = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            mapped[i] = (arg != null && modelClass.isInstance(arg))
                    ? VictorConverter.modelToEntity((MODEL) arg, entityClass)
                    : arg;
        }
        return mapped;
    }

    private Object mapResultToModel(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof Entity<?> entity) {
            return entity.toModel();
        }
        if (result instanceof Optional<?> optional) {
            return optional.map(value -> value instanceof Entity<?> entity ? entity.toModel() : value);
        }
        if (result instanceof List<?> list) {
            return list.stream().map(this::entityToModelOrSelf).toList();
        }
        if (result instanceof Collection<?> collection) {
            return collection.stream().map(this::entityToModelOrSelf).toList();
        }
        return result;
    }

    private Object entityToModelOrSelf(Object value) {
        return value instanceof Entity<?> entity ? entity.toModel() : value;
    }

    @SuppressWarnings("unchecked")
    public static <MODEL extends Model<ID>, E extends Entity<MODEL>, ID, REPO extends Repository<E, MODEL, ID>, T extends Service<MODEL,E,ID,REPO>> T createProxy(Class<T> serviceInterface, SqlExecutor sqlExecutor, Dialect dialect) {
        var handler = new ServiceProxyHandler<>(serviceInterface, sqlExecutor, dialect);
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class[]{serviceInterface},
                handler
        );
    }
}
