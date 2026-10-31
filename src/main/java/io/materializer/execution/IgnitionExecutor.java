package io.materializer.execution;

import java.util.Map;

import com.google.inject.Inject;
import io.materializer.audit.IgnitionAudit;
import io.materializer.environment.models.IgnitionEnvironment;
import io.materializer.github.models.Execution;
import io.materializer.ignition.Ignition;
import io.materializer.shared.utilities.Preconditions;

public final class IgnitionExecutor implements Executor {

    private final IgnitionEnvironment environment;
    private final Ignition ignition;
    private final GitHubOutput output;

    @Inject
    public IgnitionExecutor(IgnitionEnvironment environment, Ignition ignition, GitHubOutput output) {
        this.environment = environment;
        this.ignition = ignition;
        this.output = output;
    }

    @Override
    public void execute() {
        var audit = new IgnitionAudit(environment.auditPath(), environment.auditMetadata());
        audit.run(() -> materialize(audit));
    }

    private void materialize(IgnitionAudit audit) {
        var saved = ignition.start(environment.request(), environment.context(), audit);
        report(saved.activeExecution());
        ignition.finish(saved, audit);
    }

    private void report(Execution execution) {
        Preconditions.require(execution.childRunID() != null, "Dispatched execution has no child run ID");
        String url = environment.serverURL() + "/" + environment.context().repository() + "/actions/runs/" + execution.childRunID();
        output.outputs(
                environment.output(),
                Map.of("snapshot_sha", execution.snapshotSHA(), "execution_run_id", execution.childRunID().toString())
        );

        output.append(
                environment.summary(),
                "[Pinned pipeline execution " + execution.childRunID() + "](" + url + ")\n\nSnapshot: `" + execution.snapshotSHA() + "`\n"
        );
    }
}
