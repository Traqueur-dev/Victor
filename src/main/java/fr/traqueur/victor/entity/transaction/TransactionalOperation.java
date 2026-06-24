package fr.traqueur.victor.entity.transaction;

@FunctionalInterface
public interface TransactionalOperation {

    void execute() throws Exception;
}