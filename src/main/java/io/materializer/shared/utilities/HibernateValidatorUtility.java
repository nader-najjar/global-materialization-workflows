package io.materializer.shared.utilities;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

public final class HibernateValidatorUtility {

    public static final Validator VALIDATOR = createValidator();

    private HibernateValidatorUtility() { }

    private static Validator createValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        return factory.getValidator();
    }
}
