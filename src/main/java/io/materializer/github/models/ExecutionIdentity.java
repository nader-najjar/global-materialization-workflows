package io.materializer.github.models;

import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ExecutionIdentity(

        @Positive long runID,
        @NotNull @Pattern(regexp = ValidationPatterns.SHA) String sha,
        @NotBlank String ref

) { }
