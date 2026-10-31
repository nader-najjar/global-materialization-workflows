package io.materializer.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record IgnitionDispatchResult(

        @JsonProperty("workflow_run_id")
        @Positive
        long runID,

        @JsonProperty("html_url")
        @NotBlank
        String htmlURL

) implements GitHubResponse { }
