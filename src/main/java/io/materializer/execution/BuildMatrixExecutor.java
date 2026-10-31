package io.materializer.execution;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import com.google.inject.Inject;
import io.materializer.audit.MatrixAudit;
import io.materializer.builds.BuildMatrix;
import io.materializer.environment.models.MatrixEnvironment;
import io.materializer.github.GitHubStateStore;
import io.materializer.shared.utilities.CanonicalJsonUtility;
import io.materializer.shared.utilities.Preconditions;
import io.materializer.snapshots.models.PinSet;
import io.materializer.snapshots.models.SoftwareDefinition;

public final class BuildMatrixExecutor implements Executor {

    private final MatrixEnvironment environment;
    private final GitHubStateStore state;
    private final InputSanitizer sanitizer;
    private final GitHubOutput output;

    @Inject
    public BuildMatrixExecutor(MatrixEnvironment environment, GitHubStateStore state, InputSanitizer sanitizer, GitHubOutput output) {
        this.environment = environment;
        this.state = state;
        this.sanitizer = sanitizer;
        this.output = output;
    }

    @Override
    public void execute() {
        var audit = new MatrixAudit(environment.auditPath(), environment.auditMetadata());
        audit.run(() -> selectBuilds(audit));
    }

    private void selectBuilds(MatrixAudit audit) {
        var snapshot = executionSnapshot();
        var sources = readDefinition();
        var builds = BuildMatrix.generate(snapshot, sources.sources());
        audit.selection(environment.snapshotSHA(), snapshot, builds);
        output.outputs(
                environment.output(),
                Map.of("builds", CanonicalJsonUtility.compact(builds), "selection_attempt", Long.toString(environment.attempt()))
        );
    }

    private SoftwareDefinition readDefinition() {
        try {
            return sanitizer.parse(Files.readString(environment.definition(), StandardCharsets.UTF_8), SoftwareDefinition.class);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private PinSet executionSnapshot() {
        Preconditions.require(environment.attempt() == 1, "Submit a new materializer request instead of rerunning an execution");
        state.authorizeExecution(environment.snapshotSHA(), environment.identity());
        var snapshot = state.readSnapshot(environment.snapshotSHA());
        Preconditions.require(
                snapshot.softwareDefinitionRepository().equals(environment.github().repository()),
                "Snapshot belongs to another materializer"
        );

        Preconditions.require(
                snapshot.softwareDefinitionSHA().equals(environment.identity().sha()),
                "Execution workflow does not match the pinned materializer SHA"
        );

        return snapshot;
    }
}
