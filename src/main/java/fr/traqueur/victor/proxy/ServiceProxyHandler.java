package fr.traqueur.victor.proxy;

import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.conversion.VictorConverter;
import fr.traqueur.victor.entities.Service;
import fr.traqueur.victor.reflections.TypeResolver;
import fr.traqueur.victor.database.SqlExecutor;
import fr.traqueur.victor.entities.dialect.Dialect; // ✅ CHANGEMENT: Ajout du Dialect
import fr.traqueur.victor.exceptions.VictorException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

public class ServiceProxyHandler<MODEL extends Entity<ID>, DTO extends Dto<MODEL>, ID, REPO extends Repository<DTO, MODEL, ID>>
        implements InvocationHandler {

    private final Class<MODEL> modelClass;
    private final Class<DTO> dtoClass;
    private final REPO repository;

    @SuppressWarnings("unchecked")
    public ServiceProxyHandler(Class<? extends Service<?,?,?,?>> serviceInterface, SqlExecutor sqlExecutor, Dialect dialect) {
        var typeInfo = TypeResolver.resolveServiceTypes(serviceInterface);
        this.modelClass = (Class<MODEL>) typeInfo.modelClass();
        this.dtoClass = (Class<DTO>) typeInfo.dtoClass();
        Class<REPO> repositoryInterface = (Class<REPO>) typeInfo.repositoryClass();
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
            DTO dto = VictorConverter.modelToDto(model, dtoClass);

            DTO savedDto = repository().save(dto);
            MODEL savedModel = VictorConverter.dtoToModel(savedDto, modelClass);

            savedModel.afterSave();
            return savedModel;

        } catch (Exception e) {
            throw new VictorException("Failed to save model: " + model, e);
        }
    }

    private Optional<MODEL> findById(ID id) {
        try {
            Optional<DTO> dtoOptional = repository().findById(id);
            return dtoOptional.map(dto -> VictorConverter.dtoToModel(dto, modelClass));
        } catch (Exception e) {
            throw new VictorException("Failed to find model by ID: " + id, e);
        }
    }

    private List<MODEL> findAll() {
        try {
            List<DTO> dtos = repository().findAll();
            return dtos.stream()
                    .map(dto -> VictorConverter.dtoToModel(dto, modelClass))
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
            DTO dto = VictorConverter.modelToDto(model, dtoClass);
            repository().delete(dto);

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
    public static <T extends Service<?,?,?,?>> T createProxy(Class<T> serviceInterface, SqlExecutor sqlExecutor, Dialect dialect) {
        var handler = new ServiceProxyHandler<>(serviceInterface, sqlExecutor, dialect);
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class[]{serviceInterface},
                handler
        );
    }
}