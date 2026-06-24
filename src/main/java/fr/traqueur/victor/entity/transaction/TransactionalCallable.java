package fr.traqueur.victor.entity.transaction;

@FunctionalInterface
public interface TransactionalCallable<T> {

    T call() throws Exception;
}