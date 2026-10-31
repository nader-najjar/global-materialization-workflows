package io.materializer.audit.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Publication(

        @JsonProperty("status") PublicationStatus status,
        @JsonProperty("commit_sha") @JsonInclude(JsonInclude.Include.NON_NULL) String commitSHA

) { }
