package io.materializer.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Positive;

public record MaterializerDispatchResult(

        @JsonProperty("workflow_run_id")
        @Positive
        long runID

) implements GitHubResponse { }
