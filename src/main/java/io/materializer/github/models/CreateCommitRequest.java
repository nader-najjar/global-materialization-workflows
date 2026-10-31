package io.materializer.github.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateCommitRequest(

        @JsonProperty("message")
        @NotBlank
        String message,

        @JsonProperty("tree")
        @NotNull @Pattern(regexp = ValidationPatterns.SHA)
        String tree,

        @JsonProperty("parents")
        @NotNull
        List<@NotNull @Pattern(regexp = ValidationPatterns.SHA) String> parents

) implements GitHubRequest { }
