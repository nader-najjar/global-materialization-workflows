package io.materializer.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.utilities.Preconditions;
import io.materializer.snapshots.models.PinSet;
import io.materializer.snapshots.models.SavedPins;

public record SavedState(

        @JsonProperty("content") SavedPins content,
        @JsonProperty("commit_sha") String commitSHA,
        @JsonProperty("execution") Execution execution

) {

    public PinSet initializedContent() {
        Preconditions.require(content instanceof PinSet, "Source pins have not been initialized");
        return (PinSet) content;
    }

    public Execution activeExecution() {
        Preconditions.require(execution != null, "No execution has been prepared");
        return execution;
    }
}
