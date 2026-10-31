package io.materializer;

import java.util.Map;

import io.materializer.environment.EnvironmentVariablesFactory;
import io.materializer.execution.BuildMatrixExecutor;
import io.materializer.execution.DispatchExecutor;
import io.materializer.execution.GitHubOutput;
import io.materializer.execution.IgnitionExecutor;
import io.materializer.execution.InputSanitizer;
import io.materializer.github.GitHubAPI;
import io.materializer.github.GitHubStateStore;
import io.materializer.github.GitHubWorkflowRuns;
import io.materializer.github.Polling;
import io.materializer.ignition.Ignition;
import io.materializer.snapshots.SnapshotParser;

final class TestApplications {

    private final GitHubAPI client;
    private final Polling polling;
    private final InputSanitizer sanitizer = new InputSanitizer();
    private final GitHubOutput output = new GitHubOutput();

    TestApplications(GitHubAPI client, Polling polling) {
        this.client = client;
        this.polling = polling;
    }

    void ignite(Map<String, String> variables) {
        var state = state();
        var ignition = new Ignition(state, workflows(), sanitizer);
        new IgnitionExecutor(EnvironmentVariablesFactory.ignition(variables), ignition, output).execute();
    }

    void dispatch(Map<String, String> variables) {
        new DispatchExecutor(EnvironmentVariablesFactory.dispatch(variables), workflows(), output).execute();
    }

    void matrix(Map<String, String> variables) {
        new BuildMatrixExecutor(EnvironmentVariablesFactory.matrix(variables), state(), sanitizer, output).execute();
    }

    private GitHubStateStore state() {
        return new GitHubStateStore(client, polling, sanitizer, new SnapshotParser(sanitizer));
    }

    private GitHubWorkflowRuns workflows() {
        return new GitHubWorkflowRuns(client, polling);
    }
}
