package io.materializer.github.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record WorkflowRunsPage(

        @JsonProperty("workflow_runs")
        @NotNull
        List<@NotNull @Valid WorkflowRun> runs

) implements GitHubResponse { }
