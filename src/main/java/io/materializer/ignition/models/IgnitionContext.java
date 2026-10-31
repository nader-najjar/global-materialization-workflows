package io.materializer.ignition.models;

import io.materializer.github.models.ExecutionIdentity;
import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record IgnitionContext(

        @NotNull @Pattern(regexp = ValidationPatterns.REPOSITORY) String repository,
        @NotNull @Valid ExecutionIdentity identity,
        @Positive long attempt,
        @NotBlank String event

) {

    public String executionRef() {
        return "pipeline-executions/" + identity.runID() + "-" + attempt;
    }
}
