package io.materializer.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateReferenceRequest(

        @JsonProperty("ref")
        @NotBlank
        String ref,

        @JsonProperty("sha")
        @NotNull @Pattern(regexp = ValidationPatterns.SHA)
        String sha

) implements GitHubRequest { }
