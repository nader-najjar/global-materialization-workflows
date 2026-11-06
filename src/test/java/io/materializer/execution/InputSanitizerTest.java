package io.materializer.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.materializer.execution.exceptions.InvalidInputException;
import io.materializer.execution.models.ApplicationInput;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputSanitizerTest {

    private final InputSanitizer sanitizer = new InputSanitizer();

    @Test
    void readsTheRequestedOperation() {
        ApplicationInput input = sanitizer.sanitize(new String[] {"{\"operation\":\"ignite\"}"});

        assertEquals("ignite", input.operation());
    }

    @Test
    void rejectsMissingArguments() {
        assertThrows(InvalidInputException.class, () -> sanitizer.sanitize(null));
        assertThrows(InvalidInputException.class, () -> sanitizer.sanitize(new String[0]));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{",
        "{}",
        "[]",
        "{\"operation\":\"ignite\"} {}",
        "{\"operation\":\"ignite\",\"operation\":\"other\"}"
    })
    void rejectsMalformedMissingDuplicateAndTrailingJSON(String json) {
        InvalidInputException exception = assertThrows(
                InvalidInputException.class,
                () -> sanitizer.sanitize(new String[] {json})
        );

        assertInstanceOf(JsonProcessingException.class, exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{\"operation\":null}", "{\"operation\":\" \"}"})
    void rejectsNullOrBlankInputThroughSharedValidation(String json) {
        InvalidInputException exception = assertThrows(
                InvalidInputException.class,
                () -> sanitizer.sanitize(new String[] {json})
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
    }
}
