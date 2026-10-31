package io.materializer.snapshots.models;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record LegacyPins(

        @JsonValue
        @NotNull
        Map<@NotNull @Pattern(regexp = ValidationPatterns.REPOSITORY) String, @NotNull @Pattern(regexp = ValidationPatterns.SHA) String> pins

) implements SavedPins {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public LegacyPins {
        if (pins != null) {
            pins = Collections.unmodifiableMap(new TreeMap<>(pins));
        }
    }
}
