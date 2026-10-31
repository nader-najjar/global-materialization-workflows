package io.materializer.ignition.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MaterializationRequest(

        @JsonProperty("selection_mode")
        @NotNull
        SelectionMode selectionMode,

        @JsonProperty("source_repository")
        @Pattern(regexp = ValidationPatterns.REPOSITORY)
        String sourceRepository,

        @JsonProperty("source_sha")
        @Pattern(regexp = ValidationPatterns.SHA)
        String sourceSHA

) {

    @AssertTrue(message = "Provide source_repository and source_sha together")
    public boolean isSourcePairComplete() {
        return (sourceRepository == null) == (sourceSHA == null);
    }

    @AssertTrue(message = "A bounce uses the active snapshot; omit source_repository and source_sha")
    public boolean isBounceSourceOmitted() {
        return selectionMode != SelectionMode.BOUNCE || sourceRepository == null && sourceSHA == null;
    }

    @AssertTrue(message = "Provide source_repository and source_sha")
    public boolean isSourceProvided() {
        return selectionMode == null || selectionMode == SelectionMode.BOUNCE || sourceRepository != null && sourceSHA != null;
    }
}
