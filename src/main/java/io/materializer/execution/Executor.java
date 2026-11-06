package io.materializer.execution;

import com.google.inject.Inject;
import io.materializer.execution.models.ApplicationInput;

public class Executor {

    private final InputSanitizer inputSanitizer;

    @Inject
    public Executor(InputSanitizer inputSanitizer) {
        this.inputSanitizer = inputSanitizer;
    }

    public void execute(String[] args) {
        ApplicationInput input = inputSanitizer.sanitize(args);
        throw new UnsupportedOperationException("Operation is not implemented yet: " + input.operation());
    }
}
