package fr.traqueur.victor.exceptions;

public class VictorException extends RuntimeException {

    public VictorException(String message) {
        super(message);
    }

    public VictorException(String message, Throwable cause) {
        super(message, cause);
    }

    public VictorException(Throwable cause) {
        super(cause);
    }
}