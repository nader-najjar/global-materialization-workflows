package io.materializer.environment.models;

import java.nio.file.Path;

import io.materializer.ignition.models.MaterializationRequest;
import io.materializer.ignition.models.SelectionMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record DispatchEnvironment(

        @NotNull @Valid MaterializationRequest request,
        @NotNull @Valid GitHubSettings github,
        @NotNull Path output,
        @NotNull Path summary

) implements EnvironmentVariables {

    @AssertTrue(message = "Only materialize and dry_run can be dispatched from a source repository")
    public boolean isDispatchable() {
        return request == null || request.selectionMode() != SelectionMode.BOUNCE;
    }
}
