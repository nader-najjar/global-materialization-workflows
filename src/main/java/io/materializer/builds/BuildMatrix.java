package io.materializer.builds;

import java.util.Comparator;
import java.util.List;

import io.materializer.builds.models.BuildTarget;
import io.materializer.shared.utilities.Preconditions;
import io.materializer.snapshots.SnapshotSelection;
import io.materializer.snapshots.models.PinSet;
import io.materializer.snapshots.models.SourceDefinition;

public final class BuildMatrix {

    private BuildMatrix() { }

    public static List<BuildTarget> generate(PinSet snapshot, List<SourceDefinition> sources) {
        SnapshotSelection.validateSources(snapshot, sources);
        var missing = PinSet.uninitializedSources(snapshot.pins());
        Preconditions.require(missing.isEmpty(), "Source pins are not initialized for: " + String.join(", ", missing));
        return sources.stream().sorted(Comparator.comparing(SourceDefinition::sourceRepository))
                .flatMap(source -> sourceBuilds(snapshot, source).stream()).toList();
    }

    private static List<BuildTarget> sourceBuilds(PinSet snapshot, SourceDefinition source) {
        String sha = snapshot.pins().get(source.sourceRepository());
        return source.runners().stream().sorted()
                .map(runner -> new BuildTarget(source.sourceRepository(), sha, source.buildSystem(), runner)).toList();
    }
}
