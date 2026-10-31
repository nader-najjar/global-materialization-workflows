package io.materializer.shared.utilities;

public final class Preconditions {

    private Preconditions() { }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
