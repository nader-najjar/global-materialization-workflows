package io.materializer.environment.exceptions;

public final class EnvironmentVariableException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public EnvironmentVariableException(String message) {
        super(message);
    }

    public EnvironmentVariableException(String message, Throwable cause) {
        super(message, cause);
    }
}
