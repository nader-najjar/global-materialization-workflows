package io.materializer.snapshots.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record SoftwareDefinition(@JsonValue @NotNull List<@NotNull @Valid SourceDefinition> sources) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public SoftwareDefinition {
        if (sources != null) {
            sources = Collections.unmodifiableList(new ArrayList<>(sources));
        }
    }

    @AssertTrue(message = "Source repositories must be unique")
    public boolean isRepositoriesUnique() {
        return sources == null || sources.stream().filter(Objects::nonNull)
                .map(SourceDefinition::sourceRepository).distinct().count() == sources.size();
    }
}
