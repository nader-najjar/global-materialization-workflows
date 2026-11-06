package io.materializer.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import io.materializer.execution.exceptions.InvalidInputException;
import io.materializer.execution.models.ApplicationInput;
import io.materializer.shared.utilities.HibernateValidatorUtility;
import io.materializer.shared.utilities.JsonMapperUtility;
import jakarta.validation.ValidationException;

public class InputSanitizer {

    @Inject
    public InputSanitizer() { }

    public ApplicationInput sanitize(String[] args) {
        validateArgumentsStructure(args);
        String jsonString = args[0];

        ApplicationInput applicationInput;
        try {
            applicationInput = JsonMapperUtility.MAPPER.readValue(jsonString, ApplicationInput.class);
            HibernateValidatorUtility.validate(applicationInput);

        } catch (JsonProcessingException exception) {
            throw new InvalidInputException("Invalid input JSON", exception);

        } catch (ValidationException exception) {
            throw new InvalidInputException("Input validation failed", exception);
        }

        return applicationInput;
    }

    private void validateArgumentsStructure(String[] args) {
        if (args == null || args.length == 0) {
            throw new InvalidInputException("No input provided");
        }
    }
}
