package fr.traqueur.victor.entities;

import java.util.List;
import java.util.Optional;

public interface Query<DTO extends Dto<?>> {

    enum Order {
        ASC,
        DESC
    }

    Query<DTO> select(String... columns);

    Query<DTO> where(String condition, Object... params);

    Query<DTO> and(String condition, Object... params);

    Query<DTO> or(String condition, Object... params);

    Query<DTO> join(String table, String condition);

    Query<DTO> leftJoin(String table, String condition);

    Query<DTO> rightJoin(String table, String condition);

    Query<DTO> orderBy(String column, Order order);

    default Query<DTO> orderByAsc(String column) {
        return orderBy(column, Order.ASC);
    }

    default Query<DTO> orderByDesc(String column) {
        return orderBy(column, Order.DESC);
    }

    Query<DTO> limit(int limit);

    Query<DTO> offset(int offset);

    Query<DTO> groupBy(String... columns);

    Query<DTO> having(String condition, Object... params);

    List<DTO> findAll();

    Optional<DTO> findOne();

    DTO findFirst();

    long count();

    boolean exists();
}