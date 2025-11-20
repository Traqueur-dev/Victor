package fr.traqueur.victor.proxy;

import fr.traqueur.victor.entities.Dto;
import fr.traqueur.victor.entities.Entity;
import fr.traqueur.victor.entities.Query;
import fr.traqueur.victor.entities.Repository;
import fr.traqueur.victor.entities.metadata.EntityMetadata;
import fr.traqueur.victor.registries.EntityMetadataRegistry;
import fr.traqueur.victor.conversion.VictorConverter;
import fr.traqueur.victor.reflections.TypeResolver;
import fr.traqueur.victor.exceptions.VictorException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

public class RepositoryProxyHandler<DTO extends Dto<MODEL>, MODEL extends Entity<ID>, ID> 
    implements InvocationHandler {
    
    private final Class<DTO> dtoClass;
    private final Class<MODEL> modelClass;
    private final Class<ID> idClass;
    private final EntityMetadata entityMetadata;
    
    @SuppressWarnings("unchecked")
    public RepositoryProxyHandler(Class<?> repositoryInterface) {
        var typeInfo = TypeResolver.resolveRepositoryTypes(repositoryInterface);
        this.dtoClass = (Class<DTO>) typeInfo.dtoClass();
        this.modelClass = (Class<MODEL>) typeInfo.modelClass();
        this.idClass = (Class<ID>) typeInfo.idClass();
        this.entityMetadata = EntityMetadataRegistry.getInstance().getMetadata(modelClass);
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        
        return switch (methodName) {
            case "save" -> save((DTO) args[0]);
            case "findById" -> findById((ID) args[0]);
            case "findAll" -> findAll();
            case "deleteById" -> { deleteById((ID) args[0]); yield null; }
            case "delete" -> { delete((DTO) args[0]); yield null; }
            case "existsById" -> existsById((ID) args[0]);
            case "count" -> count();
            case "saveAll" -> saveAll((Iterable<DTO>) args[0]);
            case "deleteAll" -> { 
                if (args.length == 0) deleteAll();
                else deleteAll((Iterable<DTO>) args[0]);
                yield null;
            }
            case "query" -> query();
            default -> {
                if (methodName.startsWith("findBy")) {
                    yield handleDynamicFinderMethod(method, args);
                }
                throw new VictorException("Unsupported repository method: " + methodName);
            }
        };
    }
    
    private DTO save(DTO dto) {
        // TODO: Implement actual database save
        System.out.println("Saving DTO: " + dto);
        return dto;
    }
    
    private Optional<DTO> findById(ID id) {
        // TODO: Implement actual database findById
        System.out.println("Finding by ID: " + id);
        return Optional.empty();
    }
    
    private List<DTO> findAll() {
        // TODO: Implement actual database findAll
        System.out.println("Finding all entities");
        return List.of();
    }
    
    private void deleteById(ID id) {
        // TODO: Implement actual database deleteById
        System.out.println("Deleting by ID: " + id);
    }
    
    private void delete(DTO dto) {
        // TODO: Implement actual database delete
        System.out.println("Deleting DTO: " + dto);
    }
    
    private boolean existsById(ID id) {
        // TODO: Implement actual database existsById
        System.out.println("Checking existence for ID: " + id);
        return false;
    }
    
    private long count() {
        // TODO: Implement actual database count
        System.out.println("Counting entities");
        return 0L;
    }
    
    private List<DTO> saveAll(Iterable<DTO> dtos) {
        // TODO: Implement actual database saveAll
        System.out.println("Saving multiple DTOs");
        return List.of();
    }
    
    private void deleteAll(Iterable<DTO> dtos) {
        // TODO: Implement actual database deleteAll with DTOs
        System.out.println("Deleting multiple DTOs");
    }
    
    private void deleteAll() {
        // TODO: Implement actual database deleteAll
        System.out.println("Deleting all entities");
    }
    
    private Query<DTO> query() {
        // TODO: Create actual query builder
        return new QueryProxyHandler<>(dtoClass, entityMetadata).createProxy();
    }
    
    private Object handleDynamicFinderMethod(Method method, Object[] args) {
        // TODO: Parse method name and create appropriate query
        // Examples: findByUsername, findByEmailAndActive, findByAgeGreaterThan
        String methodName = method.getName();
        System.out.println("Dynamic finder method: " + methodName + " with args: " + 
                          (args != null ? java.util.Arrays.toString(args) : "none"));
        
        if (method.getReturnType() == Optional.class) {
            return Optional.empty();
        } else if (List.class.isAssignableFrom(method.getReturnType())) {
            return List.of();
        } else {
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> repositoryInterface) {
        var handler = new RepositoryProxyHandler<>(repositoryInterface);
        return (T) Proxy.newProxyInstance(
            repositoryInterface.getClassLoader(),
            new Class[]{repositoryInterface},
            handler
        );
    }
}