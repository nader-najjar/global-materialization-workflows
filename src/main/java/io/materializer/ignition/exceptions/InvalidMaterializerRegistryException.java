package io.materializer.ignition.exceptions;

public final class InvalidMaterializerRegistryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidMaterializerRegistryException(String message) {
        super(message);
    }

    public InvalidMaterializerRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
