package io.materializer.shared.utilities;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

public final class HibernateValidatorUtility {

    public static final Validator VALIDATOR = createValidator();

    private HibernateValidatorUtility() { }

    public static <T> void validate(T value) {
        if (value == null) {
            throw new ValidationException("Value must not be null");
        }

        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(value);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));

            throw new ValidationException(errors);
        }
    }

    private static Validator createValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        return factory.getValidator();
    }

}
