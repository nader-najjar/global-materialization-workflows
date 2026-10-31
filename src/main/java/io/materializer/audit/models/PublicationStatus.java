package io.materializer.audit.models;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PublicationStatus {
    NOT_ATTEMPTED("not_attempted"), UNCONFIRMED("unconfirmed"), CONFIRMED("confirmed");

    private final String value;

    PublicationStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
