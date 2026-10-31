package io.materializer.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record GitCommit(

        @JsonProperty("tree")
        @NotNull @Valid
        CommitSHA tree

) implements GitHubResponse { }
