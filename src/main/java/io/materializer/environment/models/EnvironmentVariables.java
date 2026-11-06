package io.materializer.environment.models;

import jakarta.validation.constraints.NotBlank;

public record EnvironmentVariables(

        @NotBlank
        String githubToken

) {
    @Override
    public String toString() {
        return "EnvironmentVariables[githubToken=REDACTED]";
    }
}
