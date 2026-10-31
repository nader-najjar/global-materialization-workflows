package io.materializer.builds.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BuildTarget(

        @JsonProperty("source_repository")
        @NotNull
        @Pattern(regexp = ValidationPatterns.REPOSITORY)
        String sourceRepository,

        @JsonProperty("source_sha")
        @NotNull
        @Pattern(regexp = ValidationPatterns.SHA)
        String sourceSHA,

        @JsonProperty("build_system")
        @NotBlank
        String buildSystem,

        @JsonProperty("runner")
        @NotBlank
        String runner

) { }
