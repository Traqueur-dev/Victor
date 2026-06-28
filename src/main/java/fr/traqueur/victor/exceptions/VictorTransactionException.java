package fr.traqueur.victor.exceptions;

public class VictorTransactionException extends VictorException {
    
    public VictorTransactionException(String message) {
        super(message);
    }
    
    public VictorTransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}