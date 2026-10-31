package io.materializer.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record GitHubFile(

        @JsonProperty("type")
        @NotNull @Pattern(regexp = "file")
        String type,

        @JsonProperty("encoding")
        @NotNull @Pattern(regexp = "base64")
        String encoding,

        @JsonProperty("content")
        @NotNull
        String content

) implements GitHubResponse { }
