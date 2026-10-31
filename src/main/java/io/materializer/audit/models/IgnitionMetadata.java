package io.materializer.audit.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record IgnitionMetadata(

        @JsonProperty("run_id") @NotBlank String runID,
        @JsonProperty("run_attempt") @NotBlank String runAttempt,
        @JsonProperty("run_number") @NotBlank String runNumber,
        @JsonProperty("invocation_sha") @NotBlank String invocationSHA,
        @JsonProperty("workflow_ref") @NotBlank String workflowRef,
        @JsonProperty("workflow_sha") @NotBlank String workflowSHA,
        @JsonProperty("implementation_repository") @NotBlank String implementationRepository,
        @JsonProperty("implementation_ref") @NotBlank String implementationRef

) { }
