package fr.traqueur.victor.proxy;

import fr.traqueur.victor.entity.Entity;
import fr.traqueur.victor.entity.Model;
import fr.traqueur.victor.entity.Repository;
import fr.traqueur.victor.conversion.VictorConverter;
import fr.traqueur.victor.entity.Service;
import fr.traqueur.victor.reflections.TypeResolver;
import fr.traqueur.victor.database.SqlExecutor;
import fr.traqueur.victor.entity.dialect.Dialect; // ✅ CHANGEMENT: Ajout du Dialect
import fr.traqueur.victor.exceptions.VictorException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

public class ServiceProxyHandler<MODEL extends Model<ID>, E extends Entity<MODEL>, ID, REPO extends Repository<E, MODEL, ID>>
        implements InvocationHandler {

    private final Class<MODEL> modelClass;
    private final Class<E> entityClass;
    private final REPO repository;

    public ServiceProxyHandler(Class<? extends Service<MODEL,E,ID,REPO>> serviceInterface, SqlExecutor sqlExecutor, Dialect dialect) {
        var typeInfo = TypeResolver.resolveServiceTypes(serviceInterface);
        this.modelClass = typeInfo.modelClass();
        this.entityClass = typeInfo.entityClass();
        Class<REPO> repositoryInterface = typeInfo.repositoryClass();
        this.repository = RepositoryProxyHandler.createProxy(
                repositoryInterface,
                sqlExecutor,
                dialect);
    }

    @SuppressWarnings("unchecked")
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

    private REPO repository() {
        return repository;
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