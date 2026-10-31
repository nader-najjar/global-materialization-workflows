package io.materializer.environment.models;

import java.nio.file.Path;

import io.materializer.audit.models.IgnitionMetadata;
import io.materializer.ignition.models.IgnitionContext;
import io.materializer.ignition.models.MaterializationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IgnitionEnvironment(

        @NotNull @Valid MaterializationRequest request,
        @NotNull @Valid IgnitionContext context,
        @NotNull @Valid GitHubSettings github,
        @NotNull Path output,
        @NotNull Path summary,
        @NotBlank String serverURL,
        @NotNull Path auditPath,
        @NotNull @Valid IgnitionMetadata auditMetadata

) implements EnvironmentVariables { }
