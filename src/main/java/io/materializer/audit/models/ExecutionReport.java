package io.materializer.audit.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.materializer.github.models.Execution;

public record ExecutionReport(

        @JsonUnwrapped Execution execution,
        @JsonProperty("conclusion") @JsonInclude(JsonInclude.Include.NON_NULL) String conclusion

) { }
