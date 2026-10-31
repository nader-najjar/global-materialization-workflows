package io.materializer.audit.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.materializer.builds.models.BuildTarget;
import io.materializer.snapshots.models.PinSet;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatrixAuditRecord(

        @JsonUnwrapped MatrixMetadata metadata,
        @JsonProperty("snapshot_sha") String snapshotSHA,
        @JsonProperty("snapshot") PinSet snapshot,
        @JsonProperty("builds") List<BuildTarget> builds,
        @JsonProperty("error") String error

) { }
