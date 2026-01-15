package fr.traqueur.victor.entities.transaction;

import fr.traqueur.victor.exceptions.VictorTransactionException;
import fr.traqueur.victor.utils.VictorLogger;

import java.sql.Connection;
import java.sql.SQLException;

public final class TransactionImpl implements Transaction {
    
    private final Connection connection;
    private boolean active;
    private boolean committed;
    private boolean rolledBack;

    public TransactionImpl(Connection connection) {
        this.connection = connection;
        this.active = true;
        this.committed = false;
        this.rolledBack = false;

        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new VictorTransactionException("Failed to start transaction", e);
        }
    }
    
    @Override
    public void commit() {
        checkActive();
        
        try {
            connection.commit();
            this.committed = true;
            this.active = false;
        } catch (SQLException e) {
            throw new VictorTransactionException("Failed to commit transaction", e);
        }
    }
    
    @Override
    public void rollback() {
        if (!active) {
            return;
        }
        
        try {
            connection.rollback();
            this.rolledBack = true;
            this.active = false;
        } catch (SQLException e) {
            throw new VictorTransactionException("Failed to rollback transaction", e);
        }
    }
    
    @Override
    public boolean isActive() {
        return active;
    }
    
    @Override
    public boolean isCommitted() {
        return committed;
    }
    
    @Override
    public boolean isRolledBack() {
        return rolledBack;
    }

    @Override
    public void close() {
        if (active) {
            rollback();
        }

        try {
            if (!connection.isClosed()) {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            VictorLogger.warn("Failed to restore auto-commit: {}", e.getMessage());
        }

        TransactionContext.clearCurrentTransaction();
    }
    
    /**
     * Retourne la connexion associée à cette transaction.
     * 
     * @return La connexion JDBC
     */
    public Connection getConnection() {
        return connection;
    }
    
    private void checkActive() {
        if (!active) {
            throw new VictorTransactionException("Transaction is not active");
        }
    }
}