package io.materializer.audit.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record MatrixMetadata(

        @JsonProperty("run_id") @NotBlank String runID,
        @JsonProperty("run_attempt") @NotBlank String runAttempt,
        @JsonProperty("workflow_sha") @NotBlank String workflowSHA

) { }
