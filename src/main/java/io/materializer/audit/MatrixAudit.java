package io.materializer.audit;

import java.nio.file.Path;
import java.util.List;

import io.materializer.audit.models.MatrixAuditRecord;
import io.materializer.audit.models.MatrixMetadata;
import io.materializer.builds.models.BuildTarget;
import io.materializer.snapshots.models.PinSet;

public final class MatrixAudit extends SelectionAudit<MatrixAuditRecord> {

    private final MatrixMetadata metadata;
    private String snapshotSHA;
    private PinSet snapshot;
    private List<BuildTarget> builds;

    public MatrixAudit(Path path, MatrixMetadata metadata) {
        super(path);
        this.metadata = metadata;
    }

    public void selection(String sha, PinSet selected, List<BuildTarget> targets) {
        snapshotSHA = sha;
        snapshot = selected;
        builds = targets;
    }

    @Override
    protected MatrixAuditRecord record() {
        return new MatrixAuditRecord(metadata, snapshotSHA, snapshot, builds, error());
    }
}
