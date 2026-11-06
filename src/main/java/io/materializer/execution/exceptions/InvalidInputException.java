package io.materializer.execution.exceptions;

/**
 * Thrown when input validation or parsing fails.
 */
public final class InvalidInputException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
