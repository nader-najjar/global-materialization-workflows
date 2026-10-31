package io.materializer.github.models;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ExecutionPhase {
    PREPARED("prepared"), DISPATCHING("dispatching"), RUNNING("running");

    private final String value;

    ExecutionPhase(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ExecutionPhase parse(String value) {
        return Arrays.stream(values()).filter(phase -> phase.value.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid execution phase: " + value));
    }
}
