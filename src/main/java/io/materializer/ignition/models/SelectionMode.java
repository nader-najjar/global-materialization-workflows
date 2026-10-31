package io.materializer.ignition.models;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SelectionMode {
    DRY_RUN("dry_run"), MATERIALIZE("materialize"), BOUNCE("bounce");

    private final String value;

    SelectionMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static SelectionMode parse(String value) {
        return Arrays.stream(values()).filter(mode -> mode.value.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid selection mode: " + value));
    }
}
