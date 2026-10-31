package io.materializer.execution;

import java.util.Map;

import com.google.inject.Inject;
import io.materializer.environment.models.DispatchEnvironment;
import io.materializer.github.GitHubWorkflowRuns;
import io.materializer.ignition.models.SelectionMode;

public final class DispatchExecutor implements Executor {

    private final DispatchEnvironment environment;
    private final GitHubWorkflowRuns workflows;
    private final GitHubOutput output;

    @Inject
    public DispatchExecutor(DispatchEnvironment environment, GitHubWorkflowRuns workflows, GitHubOutput output) {
        this.environment = environment;
        this.workflows = workflows;
        this.output = output;
    }

    @Override
    public void execute() {
        var run = workflows.dispatchIgnition(environment.request());
        output.outputs(environment.output(), Map.of("run_id", Long.toString(run.runID()), "run_url", run.htmlURL()));
        output.append(
                environment.summary(),
                "[Central " + environment.request().selectionMode().value() + " run " + run.runID() + "](" + run.htmlURL() + ")\n"
        );

        System.out.println("Central run " + run.runID() + ": " + run.htmlURL());
        if (environment.request().selectionMode() == SelectionMode.DRY_RUN) {
            workflows.waitForIgnition(run.runID());
        }
    }
}
