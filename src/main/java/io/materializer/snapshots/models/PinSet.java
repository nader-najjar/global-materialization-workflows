package io.materializer.snapshots.models;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PinSet(

        @JsonProperty("schema_version")
        @Min(2)
        @Max(2)
        int schemaVersion,

        @JsonProperty("software_definition_repository")
        @NotNull
        @Pattern(regexp = ValidationPatterns.REPOSITORY)
        String softwareDefinitionRepository,

        @JsonProperty("pins")
        @NotNull
        Map<@NotNull @Pattern(regexp = ValidationPatterns.REPOSITORY) String, @Pattern(regexp = ValidationPatterns.SHA) String> pins

) implements SavedPins {

    public PinSet {
        if (pins != null) {
            pins = Collections.unmodifiableMap(new TreeMap<>(pins));
        }
    }

    public PinSet(String repository, Map<String, String> pins) {
        this(2, repository, pins);
    }

    public String softwareDefinitionSHA() {
        return pins.get(softwareDefinitionRepository);
    }

    @AssertTrue(message = "Software definition SHA must be a full lowercase commit SHA")
    public boolean isSoftwareDefinitionPinned() {
        return pins == null || softwareDefinitionRepository == null || softwareDefinitionSHA() != null;
    }

    public static List<String> uninitializedSources(Map<String, String> pins) {
        return pins.entrySet().stream().filter(entry -> entry.getValue() == null)
                .map(entry -> entry.getKey()).sorted().toList();
    }
}
