package io.materializer.ignition.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RepositoryRevision(

        @JsonProperty("repository")
        @NotNull
        @Pattern(regexp = "[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        String repository,

        @JsonProperty("commitSHA")
        @NotNull
        @Pattern(regexp = "[0-9a-f]{40}")
        String commitSHA

) { }
