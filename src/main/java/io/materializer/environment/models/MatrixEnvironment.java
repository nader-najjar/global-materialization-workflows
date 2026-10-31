package io.materializer.environment.models;

import java.nio.file.Path;

import io.materializer.audit.models.MatrixMetadata;
import io.materializer.github.models.ExecutionIdentity;
import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record MatrixEnvironment(

        @NotNull @Pattern(regexp = ValidationPatterns.SHA) String snapshotSHA,
        @NotNull @Valid ExecutionIdentity identity,
        @Positive long attempt,
        @NotNull Path definition,
        @NotNull Path output,
        @NotNull @Valid GitHubSettings github,
        @NotNull Path auditPath,
        @NotNull @Valid MatrixMetadata auditMetadata

) implements EnvironmentVariables { }
