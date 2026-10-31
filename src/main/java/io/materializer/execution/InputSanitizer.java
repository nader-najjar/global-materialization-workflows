package io.materializer.execution;

import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.inject.Inject;
import io.materializer.execution.exceptions.InvalidInputException;
import io.materializer.shared.utilities.HibernateValidatorUtility;
import io.materializer.shared.utilities.JsonMapperUtility;

public final class InputSanitizer {

    @Inject
    public InputSanitizer() { }

    public <T> T parse(String content, Class<T> type) {
        return read(content, JsonMapperUtility.MAPPER.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    public <T> T parseResponse(String content, Class<T> type) {
        return read(content, JsonMapperUtility.MAPPER.readerFor(type));
    }

    public <T> Optional<T> parseOptional(String content, Class<T> type) {
        try {
            T input = JsonMapperUtility.MAPPER.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(content);

            return Optional.ofNullable(input).map(this::validate);
        } catch (JsonProcessingException exception) {
            throw new InvalidInputException("Invalid input JSON: " + exception.getOriginalMessage(), exception);
        }
    }

    private <T> T read(String content, ObjectReader reader) {
        if (content == null || content.isBlank()) {
            throw new InvalidInputException("No input JSON provided");
        }

        try {
            T input = reader.readValue(content);
            return validate(input);
        } catch (JsonProcessingException exception) {
            throw new InvalidInputException("Invalid input JSON: " + exception.getOriginalMessage(), exception);
        }
    }

    public <T> T validate(T input) {
        if (input == null) {
            throw new InvalidInputException("Input must not be null");
        }

        var violations = HibernateValidatorUtility.VALIDATOR.validate(input);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));

            throw new InvalidInputException("Input validation failed: " + errors);
        }

        return input;
    }
}
