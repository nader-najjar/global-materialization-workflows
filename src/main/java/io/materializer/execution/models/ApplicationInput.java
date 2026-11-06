package io.materializer.execution.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ApplicationInput(
        @JsonProperty("operation")
        @NotBlank
        String operation
) { }
