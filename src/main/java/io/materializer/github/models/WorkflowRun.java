package io.materializer.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record WorkflowRun(

        @JsonProperty("id")
        @Positive
        long runID,

        @JsonProperty("run_attempt")
        @Positive
        long runAttempt,

        @JsonProperty("status")
        @NotBlank
        String status,

        @JsonProperty("conclusion")
        String conclusion,

        @JsonProperty("event")
        @NotBlank
        String event,

        @JsonProperty("head_sha")
        @NotNull @Pattern(regexp = ValidationPatterns.SHA)
        String headSHA,

        @JsonProperty("head_branch")
        @NotBlank
        String headBranch,

        @JsonProperty("path")
        @NotBlank
        String path

) implements GitHubResponse { }
