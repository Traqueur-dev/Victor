package fr.traqueur.victor.entities.transaction;

import fr.traqueur.victor.exceptions.VictorTransactionException;

import java.sql.Connection;
import java.sql.SQLException;

public final class TransactionImpl implements Transaction {
    
    private final Connection connection;
    private boolean active;
    private boolean committed;
    private boolean rolledBack;
    private final boolean autoCloseConnection;

    public TransactionImpl(Connection connection, boolean autoCloseConnection) {
        this.connection = connection;
        this.autoCloseConnection = autoCloseConnection;
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
            connection.setAutoCommit(true);
            
            if (autoCloseConnection && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Warning: Failed to close transaction properly: " + e.getMessage());
        }
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