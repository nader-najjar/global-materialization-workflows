package io.materializer.environment;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.materializer.environment.exceptions.EnvironmentVariableException;
import io.materializer.ignition.models.SelectionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentVariablesFactoryTest {

    @TempDir
    Path directory;

    private Map<String, String> variables() {
        var values = new HashMap<String, String>();
        values.putAll(Map.of(
                "GITHUB_REPOSITORY", "example/materializer",
                "GITHUB_API_URL", "https://api.invalid",
                "GITHUB_SERVER_URL", "https://github.invalid",
                "GH_TOKEN", "offline-token",
                "GITHUB_EVENT_NAME", "workflow_dispatch",
                "GITHUB_RUN_ID", "100",
                "GITHUB_RUN_ATTEMPT", "1",
                "GITHUB_RUN_NUMBER", "1",
                "GITHUB_SHA", "a".repeat(40),
                "GITHUB_REF", "refs/heads/main"
        ));

        values.putAll(Map.of(
                "GITHUB_WORKFLOW_REF", "example/materializer/.github/workflows/ignition.yml@refs/heads/main",
                "GITHUB_WORKFLOW_SHA", "a".repeat(40),
                "GITHUB_WORKSPACE", directory.toString(),
                "GITHUB_OUTPUT", directory.resolve("output").toString(),
                "GITHUB_STEP_SUMMARY", directory.resolve("summary").toString(),
                "IMPLEMENTATION_REPOSITORY", "example/global",
                "IMPLEMENTATION_REF", "b".repeat(40),
                "SELECTION_PATH", directory.resolve("selection.json").toString(),
                "SELECTION_MODE", "materialize",
                "SOURCE_REPOSITORY", "example/app"
        ));

        values.put("SOURCE_SHA", "c".repeat(40));
        return values;
    }

    @Test
    void ignitionCreatesValidatedEnvironmentRecords() {
        var result = EnvironmentVariablesFactory.ignition(variables());
        assertEquals(SelectionMode.MATERIALIZE, result.request().selectionMode());
        assertEquals("pipeline-executions/100-1", result.context().executionRef());
        assertEquals("example/materializer", result.github().repository());
        assertEquals(directory.resolve("output"), result.output());
    }

    @Test
    void everyIgnitionVariableMustBePresent() {
        var required = variables();
        required.remove("GITHUB_WORKSPACE");
        for (String name : required.keySet()) {
            var missing = variables();
            missing.remove(name);
            assertThrows(IllegalArgumentException.class, () -> EnvironmentVariablesFactory.ignition(missing), name);
        }
    }

    @Test
    void blankRequiredValuesAndInvalidIntegersAreRejected() {
        for (String name : List.of("GH_TOKEN", "GITHUB_API_URL", "GITHUB_REPOSITORY", "GITHUB_SHA", "GITHUB_RUN_ID")) {
            var values = variables();
            values.put(name, " ");
            assertThrows(EnvironmentVariableException.class, () -> EnvironmentVariablesFactory.ignition(values));
        }
        for (String value : List.of("true", "1.0", "0", "-1", "9223372036854775808")) {
            var values = variables();
            values.put("GITHUB_RUN_ID", value);
            assertThrows(EnvironmentVariableException.class, () -> EnvironmentVariablesFactory.ignition(values));
        }
    }

    @Test
    void bounceRequiresBothSourceFieldsToBeEmpty() {
        var values = variables();
        values.putAll(Map.of("SELECTION_MODE", "bounce", "SOURCE_REPOSITORY", "", "SOURCE_SHA", ""));
        var environment = EnvironmentVariablesFactory.ignition(values);
        assertNull(environment.request().sourceRepository());
        assertNull(environment.request().sourceSHA());
        values.put("SOURCE_REPOSITORY", "example/app");
        assertThrows(EnvironmentVariableException.class, () -> EnvironmentVariablesFactory.ignition(values));
    }

    @Test
    void malformedSourceAndPartiallyProvidedSourceFailBeforeExecution() {
        var values = variables();
        values.put("SOURCE_SHA", "");
        assertThrows(EnvironmentVariableException.class, () -> EnvironmentVariablesFactory.ignition(values));
        values.put("SOURCE_SHA", "c".repeat(40));
        values.put("SOURCE_REPOSITORY", "not-a-repository");
        assertThrows(EnvironmentVariableException.class, () -> EnvironmentVariablesFactory.ignition(values));
    }

    @Test
    void dispatchTargetsMaterializerAndDisallowsBounce() {
        var values = variables();
        values.put("MATERIALIZER_REPOSITORY", "example/central");
        assertEquals("example/central", EnvironmentVariablesFactory.dispatch(values).github().repository());
        values.putAll(Map.of("SELECTION_MODE", "bounce", "SOURCE_REPOSITORY", "", "SOURCE_SHA", ""));
        assertThrows(EnvironmentVariableException.class, () -> EnvironmentVariablesFactory.dispatch(values));
    }

    @Test
    void matrixNeedsItsOwnInputsAndAnAbsoluteWorkspace() {
        var values = variables();
        assertThrows(EnvironmentVariableException.class, () -> EnvironmentVariablesFactory.matrix(values));
        values.put("SNAPSHOT_SHA", "d".repeat(40));
        assertEquals(directory.resolve(".github/repositories.json"), EnvironmentVariablesFactory.matrix(values).definition());
        values.put("GITHUB_WORKSPACE", "relative");
        var failure = assertThrows(EnvironmentVariableException.class, () -> EnvironmentVariablesFactory.matrix(values));
        assertTrue(failure.getMessage().contains("absolute"));
    }
}
