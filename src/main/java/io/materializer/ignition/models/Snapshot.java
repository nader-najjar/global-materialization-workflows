package io.materializer.ignition.models;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record Snapshot(

        @JsonProperty("pipeline")
        @NotNull
        @Valid
        RepositoryRevision pipeline,

        @JsonProperty("service")
        @NotNull
        @Valid
        RepositoryRevision service,

        @JsonProperty("repositories")
        @NotNull
        Map<
                @NotBlank String,
                @NotNull @Valid RepositoryRevision
        > repositories

) {
    public Snapshot {
        repositories = repositories == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(repositories));
    }
}
