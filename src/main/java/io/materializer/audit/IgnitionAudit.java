package io.materializer.audit;

import java.nio.file.Path;
import java.util.List;

import io.materializer.audit.models.ExecutionReport;
import io.materializer.audit.models.IgnitionAuditRecord;
import io.materializer.audit.models.IgnitionMetadata;
import io.materializer.audit.models.Publication;
import io.materializer.audit.models.PublicationStatus;
import io.materializer.github.models.Execution;
import io.materializer.github.models.SavedState;
import io.materializer.ignition.models.MaterializationRequest;
import io.materializer.snapshots.models.PinSet;

public final class IgnitionAudit extends SelectionAudit<IgnitionAuditRecord> {

    private final IgnitionMetadata metadata;
    private Publication publication = new Publication(PublicationStatus.NOT_ATTEMPTED, null);
    private MaterializationRequest request;
    private SavedState baseline;
    private PinSet candidate;
    private List<String> missingSources;
    private ExecutionReport recovery;
    private ExecutionReport execution;

    public IgnitionAudit(Path path, IgnitionMetadata metadata) {
        super(path);
        this.metadata = metadata;
    }

    public void request(MaterializationRequest value) {
        request = value;
    }

    public void baseline(SavedState value) {
        baseline = value;
    }

    public void candidate(PinSet value) {
        candidate = value;
        missingSources = PinSet.uninitializedSources(value.pins());
    }

    public void publication(PublicationStatus status, String commitSHA) {
        publication = new Publication(status, commitSHA);
    }

    public void recovery(Execution value, String conclusion) {
        recovery = new ExecutionReport(value, conclusion);
    }

    public void execution(Execution value, String conclusion) {
        execution = new ExecutionReport(value, conclusion);
    }

    @Override
    protected IgnitionAuditRecord record() {
        return new IgnitionAuditRecord(metadata, publication, request, baseline, candidate, missingSources, recovery, execution, error());
    }
}
