package io.materializer.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CommitComparison(

        @JsonProperty("status")
        @NotBlank
        String status

) implements GitHubResponse { }
