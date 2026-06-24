package fr.traqueur.victor.entity;

import java.util.List;
import java.util.Optional;

public interface Query<E extends Entity<?>> {

    enum Order {
        ASC,
        DESC
    }

    Query<E> select(String... columns);

    Query<E> where(String condition, Object... params);

    Query<E> and(String condition, Object... params);

    Query<E> or(String condition, Object... params);

    Query<E> join(String table, String condition);

    Query<E> leftJoin(String table, String condition);

    Query<E> rightJoin(String table, String condition);

    Query<E> orderBy(String column, Order order);

    default Query<E> orderByAsc(String column) {
        return orderBy(column, Order.ASC);
    }

    default Query<E> orderByDesc(String column) {
        return orderBy(column, Order.DESC);
    }

    Query<E> limit(int limit);

    Query<E> offset(int offset);

    Query<E> groupBy(String... columns);

    Query<E> having(String condition, Object... params);

    List<E> findAll();

    Optional<E> findOne();

    E findFirst();

    long count();

    boolean exists();
}