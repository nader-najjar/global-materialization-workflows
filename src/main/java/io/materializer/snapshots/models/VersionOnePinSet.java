package io.materializer.snapshots.models;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VersionOnePinSet(

        @JsonProperty("schema_version")
        @Min(1)
        @Max(1)
        int schemaVersion,

        @JsonProperty("pins")
        @NotNull
        Map<@NotNull @Pattern(regexp = ValidationPatterns.REPOSITORY) String, @Pattern(regexp = ValidationPatterns.SHA) String> pins,

        @JsonProperty("sources")
        @NotNull
        @Valid
        SoftwareDefinition sources

) implements SavedPins {

    public VersionOnePinSet {
        if (pins != null) {
            pins = Collections.unmodifiableMap(new TreeMap<>(pins));
        }
    }
}
