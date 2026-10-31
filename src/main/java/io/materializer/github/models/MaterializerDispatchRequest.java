package io.materializer.github.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MaterializerDispatchRequest(

        @JsonProperty("ref")
        @NotBlank
        String ref,

        @JsonProperty("return_run_details")
        boolean returnRunDetails,

        @JsonProperty("inputs")
        @NotNull
        @Valid
        MaterializerInputs inputs

) implements GitHubRequest { }
