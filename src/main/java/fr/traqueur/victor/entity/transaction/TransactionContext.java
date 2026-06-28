package fr.traqueur.victor.entity.transaction;

import java.sql.Connection;

public final class TransactionContext {
    
    private static final ThreadLocal<TransactionImpl> CURRENT_TRANSACTION = new ThreadLocal<>();
    
    private TransactionContext() {}

    public static void setCurrentTransaction(TransactionImpl transaction) {
        CURRENT_TRANSACTION.set(transaction);
    }

    public static TransactionImpl getCurrentTransaction() {
        return CURRENT_TRANSACTION.get();
    }

    public static Connection getCurrentConnection() {
        TransactionImpl tx = CURRENT_TRANSACTION.get();
        return tx != null ? tx.getConnection() : null;
    }

    public static boolean hasActiveTransaction() {
        TransactionImpl tx = CURRENT_TRANSACTION.get();
        return tx != null && tx.isActive();
    }

    public static void clearCurrentTransaction() {
        CURRENT_TRANSACTION.remove();
    }
}