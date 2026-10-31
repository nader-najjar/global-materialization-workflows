package io.materializer.audit.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.materializer.github.models.SavedState;
import io.materializer.ignition.models.MaterializationRequest;
import io.materializer.snapshots.models.PinSet;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IgnitionAuditRecord(

        @JsonUnwrapped IgnitionMetadata metadata,
        @JsonProperty("publication") Publication publication,
        @JsonProperty("request") MaterializationRequest request,
        @JsonProperty("baseline") SavedState baseline,
        @JsonProperty("candidate") PinSet candidate,
        @JsonProperty("missing_sources") List<String> missingSources,
        @JsonProperty("recovery") ExecutionReport recovery,
        @JsonProperty("execution") ExecutionReport execution,
        @JsonProperty("error") String error

) { }
