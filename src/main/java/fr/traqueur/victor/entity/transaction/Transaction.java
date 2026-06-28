package fr.traqueur.victor.entity.transaction;

public interface Transaction extends AutoCloseable {

    void commit();

    void rollback();

    boolean isActive();

    boolean isCommitted();

    boolean isRolledBack();

    @Override
    void close();

}