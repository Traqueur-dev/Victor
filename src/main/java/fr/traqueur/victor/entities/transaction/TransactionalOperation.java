package fr.traqueur.victor.entities.transaction;

@FunctionalInterface
public interface TransactionalOperation {

    void execute() throws Exception;
}