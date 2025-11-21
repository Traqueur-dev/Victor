package fr.traqueur.victor.managers;

import fr.traqueur.victor.entities.transaction.*;
import fr.traqueur.victor.exceptions.VictorTransactionException;

import java.sql.Connection;

/**
 * Gestionnaire de transactions.
 * Responsable de créer et gérer les transactions.
 */
public final class TransactionManager {
    
    private final ConnectionManager connectionManager;
    
    public TransactionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    /**
     * Démarre une nouvelle transaction.
     * 
     * @return La transaction créée
     */
    public Transaction beginTransaction() {
        if (TransactionContext.hasActiveTransaction()) {
            throw new VictorTransactionException(
                "A transaction is already active in this thread. " +
                "Nested transactions are not supported."
            );
        }
        
        Connection connection = connectionManager.getConnection();
        TransactionImpl transaction = new TransactionImpl(connection, true);
        
        // Enregistrer dans le contexte thread-local
        TransactionContext.setCurrentTransaction(transaction);
        
        return transaction;
    }
    
    /**
     * Exécute une opération dans une transaction (sans retour).
     * Commit automatique si succès, rollback si exception.
     */
    public void executeInTransaction(TransactionalOperation operation) {
        Transaction tx = beginTransaction();
        try {
            operation.execute();
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw new VictorTransactionException("Transaction failed and was rolled back", e);
        } finally {
            tx.close();
            TransactionContext.clearCurrentTransaction();
        }
    }
    
    /**
     * Exécute une opération dans une transaction (avec retour).
     * Commit automatique si succès, rollback si exception.
     */
    public <T> T executeInTransaction(TransactionalCallable<T> callable) {
        Transaction tx = beginTransaction();
        try {
            T result = callable.call();
            tx.commit();
            return result;
        } catch (Exception e) {
            tx.rollback();
            throw new VictorTransactionException("Transaction failed and was rolled back", e);
        } finally {
            tx.close();
            TransactionContext.clearCurrentTransaction();
        }
    }
}