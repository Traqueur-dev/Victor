# Victor

A lightweight, record-based ORM for Java 21+. Victor maps **immutable record entities** to SQL
tables and keeps them separate from your **mutable domain models**, with auto-migration, derived
queries, relationships and transactions across H2, SQLite, MySQL/MariaDB and PostgreSQL.

## Core idea: Entity vs Model

- **`Entity`** — an immutable `record` that is mapped to a table (annotations, columns,
  relationships). This is what the ORM persists and loads.
- **`Model`** — a plain mutable class holding your domain object. This is what your application
  code manipulates.

Each entity converts to/from its model through `toModel()` (instance) and `fromModel(model)`
(static). The `Model` side is only required when you use the **Service** layer.

```java
// Domain model (mutable POJO)
public class User implements Model<Long> {
    private Long id; private String username; private Integer age;
    public User() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    // ... getters/setters
}

// Persistence entity (immutable record)
@Table(table = "users")
public record UserEntity(
    @Id Long id,
    @Column(nullable = false, unique = true, length = 50) String username,
    @Column Integer age
) implements Entity<User> {

    @Override public User toModel() {
        User u = new User(); u.setId(id); u.setUsername(username); u.setAge(age); return u;
    }
    public static UserEntity fromModel(User u) {
        return new UserEntity(u.getId(), u.getUsername(), u.getAge());
    }
}
```

## Getting started

```java
Victor victor = Victor.configure()
        .h2().database("mem:app")     // or .sqlite().file(...), .mysql()/.postgresql()/.mariadb().host(..).port(..).database(..).credentials(..)
        .autoMigrate()                // create/upgrade tables from your entities
        .showSql()                    // optional: log SQL
        .entities(UserEntity.class)   // or .autoScanEntities("com.example")
        .build();

UserRepository repo = victor.createRepository(UserRepository.class);
UserEntity saved = repo.save(new UserEntity(null, "ada", 36));
Optional<UserEntity> found = repo.findById(saved.id());

victor.close();
```

You can also point at any JDBC URL with `.url("jdbc:postgresql://...").autoDetectDialect()`.

## Repositories

Declare an interface extending `Repository<Entity, Model, Id>`:

```java
public interface UserRepository extends Repository<UserEntity, User, Long> {

    // Derived queries: findBy / countBy / existsBy / deleteBy
    Optional<UserEntity> findByUsername(String username);
    List<UserEntity> findByActiveOrderByUsernameAsc(boolean active);
    long countByActive(boolean active);

    // Custom SQL (positional ? or named :param)
    @Query("SELECT * FROM users WHERE age > ? AND active = ?")
    List<UserEntity> olderActive(int age, boolean active);

    @Query("UPDATE users SET active = :active WHERE age < :maxAge")
    int deactivateYoungerThan(boolean active, int maxAge);
}
```

Built-in methods: `save`, `saveAll` (real JDBC batch, atomic), `findById`, `findAll`, `deleteById`,
`delete`, `existsById`, `count`, `deleteAll`, and a fluent `query()` builder
(`where/and/or/join/orderBy/limit/offset/groupBy/having` + `findAll/findOne/findFirst/count/exists`).

Supported derived operators: `Equal`, `NotEqual`, `GreaterThan(Equal)`, `LessThan(Equal)`, `Like`,
`NotLike`, `IsNull`, `IsNotNull`, `In`, `NotIn`, plus `OrderBy...Asc/Desc`. For anything else
(`BETWEEN`, `LIKE` prefixes, pagination beyond `query().limit()`), use `@Query` or `query()`.

## Services

A `Service<Model, Entity, Id, Repository>` works in terms of **models** and adds lifecycle hooks
(`beforeSave`/`afterSave`/…) and validation. Any custom method you declare is delegated to the
repository method of the same signature, converting models ⇄ entities automatically:

```java
public interface UserService extends Service<User, UserEntity, Long, UserRepository> {
    Optional<User> findByUsername(String username); // delegated -> repo.findByUsername -> Optional<User>
}

UserService users = victor.createService(UserService.class);
User u = users.save(new User(...));
```

## Relationships

```java
@ManyToOne(targetEntity = AuthorEntity.class) AuthorEntity author        // owning (FK column)
@OneToMany(mappedBy = "author") List<BookEntity> books                   // inverse
@ManyToMany(joinTable = "student_courses",
            joinColumn = "student_id", inverseJoinColumn = "course_id") List<CourseEntity> courses
@OneToOne(targetEntity = PersonEntity.class) PersonEntity person         // owning
@OneToOne(mappedBy = "person") PassportEntity passport                   // inverse
```

EAGER relationships are loaded automatically (cycles are guarded). Add `cascade = CascadeType.PERSIST`
on a `@ManyToOne` / `@OneToOne` (owning) or `@OneToMany` to insert new related entities together with
the parent. Without cascade, related entities (and all ManyToMany members) must be saved first.

## Embedded values

```java
@Embedded Audit audit;                       // flattened into the owning table
@Embedded(prefix = "net_") Money net;        // same type embedded twice with distinct prefixes
```

## Supported column types

`String`, `Boolean`, `Short`, `Byte`, `Integer`, `Long`, `Float`, `Double`, `BigDecimal`,
`LocalDate`, `LocalTime`, `LocalDateTime`, `byte[]`, `java.util.UUID`, and Java `enum`
(stored as its `name()`). Java enums and UUIDs are stored as text.

## Transactions

`save` and `saveAll` run in a transaction (entity row + FK + cascades + join rows are atomic).
For multi-statement units of work use the transaction manager / `query()` API. Nested transactions
are not supported.

## Known limitations (v1)

- **No sequence-based IDs** — use auto-generated identity/auto-increment (`@Id(autoGenerated = true)`).
- **No composite primary keys** — exactly one `@Id` per entity.
- **Cascade is PERSIST only** — no cascade delete; deleting a parent does not delete children
  (rely on DB `ON DELETE` constraints if needed).
- **ManyToMany and non-cascaded relations must be pre-saved** before they are referenced.
- **`FetchType.LAZY` means "not loaded"** — immutable records have no lazy proxy; fetch lazily
  declared relationships explicitly through the repository.
- **`java.util.Date`, `java.time.Instant`, `char`** are not mapped — use `LocalDate`/`LocalDateTime`/`String`.
