package io.materializer.ignition.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record MaterializerRegistry(

        @JsonProperty("registrations")
        @NotNull
        List<
                @NotNull @Valid MaterializerRegistration
        > registrations

) {
    public MaterializerRegistry {
        registrations = registrations == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(registrations));
    }
}
