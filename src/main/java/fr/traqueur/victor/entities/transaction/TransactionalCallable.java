package fr.traqueur.victor.entities.transaction;

@FunctionalInterface
public interface TransactionalCallable<T> {

    T call() throws Exception;
}