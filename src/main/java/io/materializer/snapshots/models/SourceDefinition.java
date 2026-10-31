package io.materializer.snapshots.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SourceDefinition(

        @JsonProperty("source_repository")
        @NotNull
        @Pattern(regexp = ValidationPatterns.REPOSITORY)
        String sourceRepository,

        @JsonProperty("build_system")
        @NotBlank
        String buildSystem,

        @JsonProperty("runners")
        @NotEmpty
        List<@NotBlank String> runners

) {

    public SourceDefinition {
        if (runners != null) {
            runners = Collections.unmodifiableList(new ArrayList<>(runners));
        }
    }

    @AssertTrue(message = "Source runners must be unique")
    public boolean isRunnersUnique() {
        return runners == null || runners.stream().distinct().count() == runners.size();
    }
}
