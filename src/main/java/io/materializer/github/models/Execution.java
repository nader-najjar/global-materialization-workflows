package io.materializer.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record Execution(

        @JsonProperty("phase")
        @NotNull
        ExecutionPhase phase,

        @JsonProperty("launcher_run_id")
        @Positive
        long launcherRunID,

        @JsonProperty("launcher_attempt")
        @Min(1)
        @Max(1)
        long launcherAttempt,

        @JsonProperty("snapshot_sha")
        @NotNull
        @Pattern(regexp = ValidationPatterns.SHA)
        String snapshotSHA,

        @JsonProperty("materializer_sha")
        @NotNull
        @Pattern(regexp = ValidationPatterns.SHA)
        String materializerSHA,

        @JsonProperty("execution_ref")
        @NotNull
        String executionRef,

        @JsonProperty("child_run_id")
        @Positive
        Long childRunID

) {

    @AssertTrue(message = "Execution ref does not match its launcher")
    public boolean isRefConsistent() {
        return executionRef == null || executionRef.equals("pipeline-executions/" + launcherRunID + "-" + launcherAttempt);
    }

    @AssertTrue(message = "Execution phase does not match its child run")
    public boolean isChildConsistent() {
        return phase == null || (phase == ExecutionPhase.RUNNING) == (childRunID != null);
    }

    public Execution dispatching() {
        return new Execution(ExecutionPhase.DISPATCHING, launcherRunID, launcherAttempt, snapshotSHA, materializerSHA, executionRef, null);
    }

    public Execution running(long runID) {
        return new Execution(ExecutionPhase.RUNNING, launcherRunID, launcherAttempt, snapshotSHA, materializerSHA, executionRef, runID);
    }
}
