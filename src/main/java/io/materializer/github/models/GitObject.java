package io.materializer.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record GitObject(

        @JsonProperty("type")
        @NotNull @Pattern(regexp = "commit")
        String type,

        @JsonProperty("sha")
        @NotNull @Pattern(regexp = ValidationPatterns.SHA)
        String sha

) { }
