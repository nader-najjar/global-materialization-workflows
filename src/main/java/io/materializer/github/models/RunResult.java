package io.materializer.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record RunResult(

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
        String conclusion

) implements GitHubResponse { }
