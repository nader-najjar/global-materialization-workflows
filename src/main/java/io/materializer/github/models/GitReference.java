package io.materializer.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record GitReference(

        @JsonProperty("object")
        @NotNull @Valid
        GitObject object

) implements GitHubResponse { }
