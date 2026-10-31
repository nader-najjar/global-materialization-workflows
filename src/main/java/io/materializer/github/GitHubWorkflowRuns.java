package io.materializer.github;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.google.inject.Inject;
import io.materializer.github.models.Execution;
import io.materializer.github.models.IgnitionDispatchRequest;
import io.materializer.github.models.IgnitionDispatchResult;
import io.materializer.github.models.MaterializerDispatchRequest;
import io.materializer.github.models.MaterializerDispatchResult;
import io.materializer.github.models.MaterializerInputs;
import io.materializer.github.models.RunResult;
import io.materializer.github.models.WorkflowRun;
import io.materializer.github.models.WorkflowRunsPage;
import io.materializer.ignition.models.MaterializationRequest;
import io.materializer.ignition.models.SelectionMode;
import io.materializer.shared.utilities.Preconditions;

public final class GitHubWorkflowRuns {

    private static final String WORKFLOW = "materializer.yml";
    private final GitHubAPI client;
    private final Polling polling;

    @Inject
    public GitHubWorkflowRuns(GitHubAPI client, Polling polling) {
        this.client = client;
        this.polling = polling;
    }

    public void validateMaterializerRun(WorkflowRun run, Execution execution) {
        Preconditions.require(Long.valueOf(run.runID()).equals(execution.childRunID()), "Child run identity changed");
        Preconditions.require(run.event().equals("workflow_dispatch"), "Child was not manually dispatched");
        Preconditions.require(run.headSHA().equals(execution.materializerSHA()), "Child workflow SHA changed");
        Preconditions.require(run.headBranch().equals(execution.executionRef()), "Child execution ref changed");
        Preconditions.require(run.path().split("@", 2)[0].equals(".github/workflows/" + WORKFLOW), "Child workflow path changed");
    }

    public long dispatchMaterializer(Execution execution) {
        var payload = new MaterializerDispatchRequest(execution.executionRef(), true, new MaterializerInputs(execution.snapshotSHA()));
        return client.request("POST", "/actions/workflows/" + WORKFLOW + "/dispatches", payload, MaterializerDispatchResult.class).runID();
    }

    private List<WorkflowRun> dispatchedMaterializerRuns(Execution execution) {
        var matches = new ArrayList<WorkflowRun>();
        int page = 0;
        boolean hasMore = true;
        while (hasMore && page < 10) {
            var result = materializerRunPage(execution, ++page);
            matches.addAll(result.runs().stream().filter(run -> run.headBranch().equals(execution.executionRef())).toList());
            hasMore = result.runs().size() == 100;
        }
        Preconditions.require(!hasMore, "Child discovery exceeded its search limit; execution ownership remains held");
        return matches;
    }

    private WorkflowRunsPage materializerRunPage(Execution execution, int page) {
        String query = "event=workflow_dispatch&head_sha=" + execution.materializerSHA() + "&per_page=100&page=" + page;
        return client.get("/actions/workflows/" + WORKFLOW + "/runs?" + query, WorkflowRunsPage.class);
    }

    public long findDispatchedMaterializerRun(Execution execution) {
        var matches = dispatchedMaterializerRuns(execution);
        Preconditions.require(
                matches.size() == 1,
                "Dispatch outcome is uncertain for " + execution.executionRef() + ": found " + matches.size()
                        + " child runs. Ownership remains held; do not redispatch or clear it without resolving that run."
        );

        long runID = matches.getFirst().runID();
        validateMaterializerRun(matches.getFirst(), execution.running(runID));
        return runID;
    }

    private RunResult completedMaterializer(Execution execution) {
        var run = client.get("/actions/runs/" + execution.childRunID(), WorkflowRun.class);
        validateMaterializerRun(run, execution);
        return run.status().equals("completed") ? new RunResult(run.runID(), run.runAttempt(), run.status(), run.conclusion()) : null;
    }

    public RunResult waitForMaterializer(Execution execution) {
        return polling.await(
                () -> completedMaterializer(execution),
                Duration.ofHours(5),
                Duration.ofSeconds(30),
                "Child is still active; execution ownership remains held for the next launcher to recover"
        );
    }

    public IgnitionDispatchResult dispatchIgnition(MaterializationRequest request) {
        Preconditions.require(
                request.selectionMode() != SelectionMode.BOUNCE,
                "Only materialize and dry_run can be dispatched from a source repository"
        );

        var payload = new IgnitionDispatchRequest("main", true, request);
        return client.request("POST", "/actions/workflows/ignition.yml/dispatches", payload, IgnitionDispatchResult.class);
    }

    private RunResult completedIgnition(long runID) {
        var result = client.get("/actions/runs/" + runID, RunResult.class);
        Preconditions.require(
                result.runID() == runID && result.runAttempt() == 1,
                "The dispatched run changed identity or was rerun; submit a new dry run"
        );

        if (!result.status().equals("completed")) {
            return null;
        }
        Preconditions.require(
                "success".equals(result.conclusion()),
                "Central dry run " + runID + " finished with: " + result.conclusion()
        );

        return result;
    }

    public void waitForIgnition(long runID) {
        polling.await(
                () -> completedIgnition(runID),
                Duration.ofHours(5),
                Duration.ofSeconds(30),
                "Timed out waiting for central dry run " + runID + "; it may still be running"
        );
    }
}
