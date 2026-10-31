package io.materializer.environment.models;

import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record GitHubSettings(

        @NotBlank String url,
        @NotNull @Pattern(regexp = ValidationPatterns.REPOSITORY) String repository,
        @NotBlank String token

) {

    @Override
    public String toString() {
        return "GitHubSettings[url=" + url + ", repository=" + repository + "]";
    }
}
