package io.materializer.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record GitTreeEntry(

        @JsonProperty("path")
        @NotBlank
        String path,

        @JsonProperty("mode")
        @NotNull @Pattern(regexp = "100644")
        String mode,

        @JsonProperty("type")
        @NotNull @Pattern(regexp = "blob")
        String type,

        @JsonProperty("content")
        @NotNull
        String content

) { }
