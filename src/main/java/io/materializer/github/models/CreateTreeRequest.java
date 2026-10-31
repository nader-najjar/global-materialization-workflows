package io.materializer.github.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.materializer.shared.utilities.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateTreeRequest(

        @JsonProperty("tree")
        @NotNull
        List<@NotNull @Valid GitTreeEntry> tree,

        @JsonProperty("base_tree")
        @JsonInclude(JsonInclude.Include.NON_NULL) @Pattern(regexp = ValidationPatterns.SHA)
        String baseTree

) implements GitHubRequest { }
