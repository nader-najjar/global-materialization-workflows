package io.materializer.execution;

import io.materializer.execution.exceptions.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutorTest {

    private final Executor executor = new Executor(new InputSanitizer());

    @Test
    void rejectsInvalidInputBeforeDispatch() {
        assertThrows(InvalidInputException.class, () -> executor.execute(new String[0]));
    }

    @Test
    void reportsUnimplementedOperationsRatherThanSilentlySucceeding() {
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> executor.execute(new String[] {"{\"operation\":\"ignite\"}"})
        );

        assertEquals("Operation is not implemented yet: ignite", exception.getMessage());
    }
}
