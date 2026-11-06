package io.materializer.environment;

import java.time.DayOfWeek;
import java.util.Map;

import io.materializer.environment.exceptions.EnvironmentVariableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentVariablesFactoryHelpersTest {

    @Test
    void preservesNonblankStringWhitespace() {
        assertEquals(" value ", EnvironmentVariablesFactory.extractString(Map.of("VALUE", " value "), "VALUE"));
    }

    @Test
    void rejectsMissingVariablesInEveryExtractor() {
        assertThrows(
                EnvironmentVariableException.class,
                () -> EnvironmentVariablesFactory.extractString(Map.of(), "KEY")
        );

        assertThrows(
                EnvironmentVariableException.class,
                () -> EnvironmentVariablesFactory.extractInt(Map.of(), "KEY")
        );

        assertThrows(
                EnvironmentVariableException.class,
                () -> EnvironmentVariablesFactory.extractBoolean(Map.of(), "KEY")
        );

        assertThrows(
                EnvironmentVariableException.class,
                () -> EnvironmentVariablesFactory.extractEnum(Map.of(), "KEY", DayOfWeek.class)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsAbsentOrBlankValuesWithTheVariableName(String value) {
        EnvironmentVariableException exception = assertThrows(
                EnvironmentVariableException.class,
                () -> EnvironmentVariablesFactory.ensureVariableExists("REQUIRED", value)
        );

        assertEquals("Required environment variable 'REQUIRED' is not set", exception.getMessage());
    }

    @Test
    void acceptsNonblankValues() {
        assertDoesNotThrow(() -> EnvironmentVariablesFactory.ensureVariableExists("VALUE", " value "));
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE})
    void parsesIntegersIncludingBothBounds(int value) {
        assertEquals(
                value,
                EnvironmentVariablesFactory.extractInt(Map.of("COUNT", Integer.toString(value)), "COUNT")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"2147483648", "-2147483649", "1.5", "one", " 1", "1 "})
    void rejectsInvalidOrOverflowingIntegers(String value) {
        EnvironmentVariableException exception = assertThrows(
                EnvironmentVariableException.class,
                () -> EnvironmentVariablesFactory.extractInt(Map.of("COUNT", value), "COUNT")
        );

        assertTrue(exception.getMessage().contains("COUNT"));
        assertInstanceOf(NumberFormatException.class, exception.getCause());
    }

    @ParameterizedTest
    @CsvSource({"true, true", "false, false"})
    void parsesExactBooleanValues(String value, boolean expected) {
        assertEquals(expected, EnvironmentVariablesFactory.extractBoolean(Map.of("ENABLED", value), "ENABLED"));
        assertEquals(expected, EnvironmentVariablesFactory.customParseBoolean("ENABLED", value));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"TRUE", "False", "1", "0", "yes", " true", "false "})
    void rejectsNonliteralBooleansInsteadOfSilentlyReturningFalse(String value) {
        EnvironmentVariableException exception = assertThrows(
                EnvironmentVariableException.class,
                () -> EnvironmentVariablesFactory.customParseBoolean("ENABLED", value)
        );

        assertTrue(exception.getMessage().contains("ENABLED"));
        assertTrue(exception.getMessage().contains("'true' or 'false'"));
    }

    @Test
    void parsesExactEnumConstants() {
        assertEquals(
                DayOfWeek.MONDAY,
                EnvironmentVariablesFactory.extractEnum(Map.of("DAY", "MONDAY"), "DAY", DayOfWeek.class)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"monday", " MONDAY", "MONDAY ", "UNKNOWN"})
    void rejectsInexactEnumNamesAndReportsAllowedValues(String value) {
        EnvironmentVariableException exception = assertThrows(
                EnvironmentVariableException.class,
                () -> EnvironmentVariablesFactory.extractEnum(Map.of("DAY", value), "DAY", DayOfWeek.class)
        );

        assertTrue(exception.getMessage().contains("DAY"));
        assertTrue(exception.getMessage().contains("MONDAY"));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }
}
