package io.materializer.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateReferenceRequest(

        @JsonProperty("sha")
        @NotNull @Pattern(regexp = ValidationPatterns.SHA)
        String sha,

        @JsonProperty("force")
        boolean force

) implements GitHubRequest { }
