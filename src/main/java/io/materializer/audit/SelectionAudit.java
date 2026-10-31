package io.materializer.audit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.materializer.shared.utilities.CanonicalJsonUtility;

public abstract class SelectionAudit<T> {

    private final Path path;
    private String error;

    protected SelectionAudit(Path path) {
        this.path = path;
    }

    public final void run(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            error = exception.getMessage();
            throw exception;
        } finally {
            write();
        }
    }

    protected final String error() {
        return error;
    }

    protected abstract T record();

    private void write() {
        try {
            Files.writeString(path, CanonicalJsonUtility.encode(record()), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
