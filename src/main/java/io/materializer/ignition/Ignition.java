package io.materializer.ignition;

import java.util.Set;

import com.google.inject.Inject;
import io.materializer.audit.IgnitionAudit;
import io.materializer.audit.models.PublicationStatus;
import io.materializer.execution.InputSanitizer;
import io.materializer.github.GitHubStateStore;
import io.materializer.github.GitHubWorkflowRuns;
import io.materializer.github.exceptions.GitHubRequestException;
import io.materializer.github.models.Execution;
import io.materializer.github.models.ExecutionPhase;
import io.materializer.github.models.SavedState;
import io.materializer.ignition.models.IgnitionContext;
import io.materializer.ignition.models.MaterializationRequest;
import io.materializer.ignition.models.SelectionMode;
import io.materializer.shared.utilities.Preconditions;
import io.materializer.snapshots.SnapshotSelection;
import io.materializer.snapshots.models.PinSet;
import io.materializer.snapshots.models.SoftwareDefinition;

public final class Ignition {

    private final GitHubStateStore state;
    private final GitHubWorkflowRuns workflows;
    private final InputSanitizer sanitizer;

    @Inject
    public Ignition(GitHubStateStore state, GitHubWorkflowRuns workflows, InputSanitizer sanitizer) {
        this.state = state;
        this.workflows = workflows;
        this.sanitizer = sanitizer;
    }

    private SavedState recoverSentExecution(SavedState saved, IgnitionAudit audit) {
        var execution = saved.activeExecution();
        if (execution.childRunID() == null) {
            execution = execution.running(workflows.findDispatchedMaterializerRun(execution));
            saved = state.save(saved.initializedContent(), execution, saved);
        }
        var result = workflows.waitForMaterializer(execution);
        audit.recovery(execution, result.conclusion());
        return saved;
    }

    public SavedState recoverExecution(SavedState saved, IgnitionAudit audit) {
        if (saved.execution() == null) {
            return saved;
        }
        audit.recovery(saved.execution(), null);
        if (saved.execution().phase() != ExecutionPhase.PREPARED) {
            saved = recoverSentExecution(saved, audit);
        }
        return state.save(saved.initializedContent(), null, saved);
    }

    private void validateSuccessor(SavedState saved, MaterializationRequest request, IgnitionContext context) {
        if (context.repository().equals(request.sourceRepository())
                && SnapshotSelection.isInitialized(saved.content()) && context.event().equals("push")) {
            String activeSHA = saved.initializedContent().softwareDefinitionSHA();
            state.validateMaterializerSuccessor(activeSHA, request.sourceSHA());
        }
    }

    private PinSet selectCandidate(SavedState saved, MaterializationRequest request, IgnitionContext context, IgnitionAudit audit) {
        var requestedPins = SnapshotSelection.applyRequest(saved.content(), request, context.repository());
        String sha = requestedPins.get(context.repository());
        validateSuccessor(saved, request, context);
        var definition = sanitizer.parse(state.readText(".github/repositories.json", sha), SoftwareDefinition.class);
        state.readText(".github/workflows/materializer.yml", sha);
        var candidate = sanitizer.validate(SnapshotSelection.select(saved.content(), request, context.repository(), definition.sources()));
        audit.candidate(candidate);
        return candidate;
    }

    private SavedState persistCandidate(SavedState saved, PinSet candidate, MaterializationRequest request, IgnitionAudit audit) {
        if (request.selectionMode() != SelectionMode.MATERIALIZE) {
            return saved;
        }
        audit.publication(PublicationStatus.UNCONFIRMED, null);
        saved = state.save(candidate, null, saved);
        audit.publication(PublicationStatus.CONFIRMED, saved.commitSHA());
        return saved;
    }

    private SavedState prepareExecution(SavedState saved, PinSet candidate, IgnitionContext context) {
        String snapshotSHA = state.publishSnapshot(candidate);
        state.publishExecutionRef(context.executionRef(), candidate.softwareDefinitionSHA());
        var execution = new Execution(
                ExecutionPhase.PREPARED,
                context.identity().runID(),
                context.attempt(),
                snapshotSHA,
                candidate.softwareDefinitionSHA(),
                context.executionRef(),
                null
        );

        return state.save(saved.initializedContent(), execution, saved);
    }

    private SavedState dispatchExecution(SavedState saved, IgnitionAudit audit) {
        var execution = saved.activeExecution().dispatching();
        saved = state.save(saved.initializedContent(), execution, saved);
        audit.execution(execution, null);
        long childRunID;
        try {
            childRunID = workflows.dispatchMaterializer(execution);
        } catch (GitHubRequestException exception) {
            releaseRejectedDispatch(exception, saved);
            throw exception;
        }
        execution = execution.running(childRunID);
        saved = state.save(saved.initializedContent(), execution, saved);
        audit.execution(execution, null);
        return saved;
    }

    private void releaseRejectedDispatch(GitHubRequestException exception, SavedState saved) {
        if (Set.of(400, 401, 403, 404, 422, 429).contains(exception.status())) {
            state.save(saved.initializedContent(), null, saved);
        }
    }

    public SavedState start(MaterializationRequest request, IgnitionContext context, IgnitionAudit audit) {
        audit.request(request);
        var saved = recoverExecution(state.load(), audit);
        Preconditions.require(context.attempt() == 1, "Submit a new materializer request instead of rerunning the launcher");
        audit.baseline(saved);
        var candidate = selectCandidate(saved, request, context, audit);
        saved = persistCandidate(saved, candidate, request, audit);
        SnapshotSelection.validateInitialization(candidate.pins(), request);
        return dispatchExecution(prepareExecution(saved, candidate, context), audit);
    }

    public void finish(SavedState saved, IgnitionAudit audit) {
        var execution = saved.activeExecution();
        var result = workflows.waitForMaterializer(execution);
        state.save(saved.initializedContent(), null, saved);
        audit.execution(execution, result.conclusion());
        Preconditions.require(result.runAttempt() == 1, "Child was rerun; submit a new materializer request");
        Preconditions.require(
                "success".equals(result.conclusion()),
                "Pinned pipeline execution " + execution.childRunID() + " finished with: " + result.conclusion()
        );
    }
}
