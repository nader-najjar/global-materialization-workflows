package io.materializer.environment;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import io.materializer.audit.models.IgnitionMetadata;
import io.materializer.audit.models.MatrixMetadata;
import io.materializer.environment.exceptions.EnvironmentVariableException;
import io.materializer.environment.models.DispatchEnvironment;
import io.materializer.environment.models.GitHubSettings;
import io.materializer.environment.models.IgnitionEnvironment;
import io.materializer.environment.models.MatrixEnvironment;
import io.materializer.github.models.ExecutionIdentity;
import io.materializer.ignition.models.IgnitionContext;
import io.materializer.ignition.models.MaterializationRequest;
import io.materializer.ignition.models.SelectionMode;
import io.materializer.shared.utilities.HibernateValidatorUtility;

public final class EnvironmentVariablesFactory {

    private EnvironmentVariablesFactory() { }

    public static IgnitionEnvironment ignition(Map<String, String> variables) {
        var context = new IgnitionContext(
                required(variables, "GITHUB_REPOSITORY"),
                identity(variables),
                integer(variables, "GITHUB_RUN_ATTEMPT"),
                required(variables, "GITHUB_EVENT_NAME")
        );

        var environment = new IgnitionEnvironment(
                request(variables),
                context,
                github(variables, context.repository()),
                path(variables, "GITHUB_OUTPUT"),
                path(variables, "GITHUB_STEP_SUMMARY"),
                required(variables, "GITHUB_SERVER_URL"),
                path(variables, "SELECTION_PATH"),
                ignitionMetadata(variables)
        );

        return validate(environment);
    }

    public static DispatchEnvironment dispatch(Map<String, String> variables) {
        var environment = new DispatchEnvironment(
                request(variables),
                github(variables, required(variables, "MATERIALIZER_REPOSITORY")),
                path(variables, "GITHUB_OUTPUT"),
                path(variables, "GITHUB_STEP_SUMMARY")
        );

        return validate(environment);
    }

    public static MatrixEnvironment matrix(Map<String, String> variables) {
        var metadata = new MatrixMetadata(
                required(variables, "GITHUB_RUN_ID"),
                required(variables, "GITHUB_RUN_ATTEMPT"),
                required(variables, "GITHUB_WORKFLOW_SHA")
        );

        var environment = new MatrixEnvironment(
                required(variables, "SNAPSHOT_SHA"),
                identity(variables),
                integer(variables, "GITHUB_RUN_ATTEMPT"),
                path(variables, "GITHUB_WORKSPACE").resolve(".github/repositories.json"),
                path(variables, "GITHUB_OUTPUT"),
                github(variables, required(variables, "GITHUB_REPOSITORY")),
                path(variables, "SELECTION_PATH"),
                metadata
        );

        return validate(environment);
    }

    private static GitHubSettings github(Map<String, String> variables, String repository) {
        return new GitHubSettings(required(variables, "GITHUB_API_URL"), repository, required(variables, "GH_TOKEN"));
    }

    public static ExecutionIdentity identity(Map<String, String> variables) {
        return validate(new ExecutionIdentity(integer(variables, "GITHUB_RUN_ID"), required(variables, "GITHUB_SHA"), required(variables, "GITHUB_REF")));
    }

    private static MaterializationRequest request(Map<String, String> variables) {
        return new MaterializationRequest(
                SelectionMode.parse(required(variables, "SELECTION_MODE")),
                sourceValue(variables, "SOURCE_REPOSITORY"),
                sourceValue(variables, "SOURCE_SHA")
        );
    }

    private static String sourceValue(Map<String, String> variables, String name) {
        if (!variables.containsKey(name)) {
            throw new EnvironmentVariableException(name + " is required");
        }
        String value = variables.get(name);
        return value == null || value.isEmpty() ? null : value;
    }

    private static IgnitionMetadata ignitionMetadata(Map<String, String> variables) {
        return new IgnitionMetadata(
                required(variables, "GITHUB_RUN_ID"),
                required(variables, "GITHUB_RUN_ATTEMPT"),
                required(variables, "GITHUB_RUN_NUMBER"),
                required(variables, "GITHUB_SHA"),
                required(variables, "GITHUB_WORKFLOW_REF"),
                required(variables, "GITHUB_WORKFLOW_SHA"),
                required(variables, "IMPLEMENTATION_REPOSITORY"),
                required(variables, "IMPLEMENTATION_REF")
        );
    }

    private static Path path(Map<String, String> variables, String name) {
        var path = Path.of(required(variables, name));
        if (!path.isAbsolute()) {
            throw new EnvironmentVariableException(name + " must be an absolute path");
        }
        return path;
    }

    private static long integer(Map<String, String> variables, String name) {
        long value = parseInteger(required(variables, name), name);
        if (value <= 0) {
            throw new EnvironmentVariableException(name + " must be a positive integer");
        }
        return value;
    }

    private static long parseInteger(String value, String name) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException exception) {
            throw new EnvironmentVariableException(name + " must be a positive integer", exception);
        }
    }

    private static String required(Map<String, String> variables, String name) {
        String value = variables.get(name);
        if (value == null || value.isBlank()) {
            throw new EnvironmentVariableException(name + " is required");
        }
        return value;
    }

    private static <T> T validate(T environment) {
        var violations = HibernateValidatorUtility.VALIDATOR.validate(environment);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .sorted().collect(Collectors.joining(", "));

            throw new EnvironmentVariableException("Environment validation failed: " + errors);
        }
        return environment;
    }
}
