package io.materializer.snapshots;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.materializer.ignition.models.MaterializationRequest;
import io.materializer.ignition.models.SelectionMode;
import io.materializer.shared.utilities.Preconditions;
import io.materializer.snapshots.models.PinSet;
import io.materializer.snapshots.models.SavedPins;
import io.materializer.snapshots.models.SourceDefinition;
import io.materializer.snapshots.models.VersionOnePinSet;

public final class SnapshotSelection {

    private SnapshotSelection() { }

    private static Map<String, String> versionOnePins(VersionOnePinSet previous, String repository) {
        var names = sourceNames(repository, previous.sources().sources());
        Preconditions.require(previous.pins().keySet().equals(names), "Version 1 pins do not match their definitions");
        Preconditions.require(previous.pins().get(repository) != null, "Saved materializer SHA is required");
        return new TreeMap<>(previous.pins());
    }

    public static Map<String, String> previousPins(SavedPins previous, String repository) {
        if (previous == null) {
            return new TreeMap<>();
        }
        if (previous instanceof VersionOnePinSet versionOne) {
            return versionOnePins(versionOne, repository);
        }
        if (previous instanceof PinSet snapshot) {
            Preconditions.require(snapshot.softwareDefinitionRepository().equals(repository), "Snapshot belongs to another materializer");
        }
        return new TreeMap<>(previous.pins());
    }

    public static boolean isInitialized(SavedPins previous) {
        return previous instanceof PinSet;
    }

    public static Map<String, String> applyRequest(SavedPins previous, MaterializationRequest request, String repository) {
        var pins = previousPins(previous, repository);
        if (!isInitialized(previous)) {
            Preconditions.require(
                    request.selectionMode() == SelectionMode.MATERIALIZE && repository.equals(request.sourceRepository()),
                    "Materialize the materializer repository and its SHA to initialize or migrate source-pins.json first"
            );
        }
        if (request.sourceRepository() != null) {
            pins.put(request.sourceRepository(), request.sourceSHA());
        }
        Preconditions.require(pins.get(repository) != null, "Software definition SHA is required");
        return pins;
    }

    public static PinSet select(SavedPins previous, MaterializationRequest request, String repository, List<SourceDefinition> sources) {
        var pins = applyRequest(previous, request, repository);
        var names = sourceNames(repository, sources);
        if (request.sourceRepository() != null) {
            Preconditions.require(names.contains(request.sourceRepository()), "Source repository is not registered in the selected definition");
        }
        if (isInitialized(previous) && !repository.equals(request.sourceRepository())) {
            Preconditions.require(pins.keySet().equals(names), "Saved pins do not match their pinned software definition");
        }
        var selected = new TreeMap<String, String>();
        names.forEach(name -> selected.put(name, pins.get(name)));
        return new PinSet(repository, selected);
    }

    public static Set<String> sourceNames(String repository, List<SourceDefinition> sources) {
        var names = new HashSet<String>();
        names.add(repository);
        sources.forEach(source -> names.add(source.sourceRepository()));
        return names;
    }

    public static void validateSources(PinSet snapshot, List<SourceDefinition> sources) {
        Preconditions.require(
                snapshot.pins().keySet().equals(sourceNames(snapshot.softwareDefinitionRepository(), sources)),
                "Saved pins must match the materializer and registered sources"
        );
    }

    public static void validateInitialization(Map<String, String> pins, MaterializationRequest request) {
        var missing = PinSet.uninitializedSources(pins);
        if (missing.isEmpty()) {
            return;
        }
        String message = "Source pins are not initialized for: " + String.join(", ", missing) + ". No builds were started.";
        if (request.selectionMode() == SelectionMode.MATERIALIZE) {
            message = "Initialization incomplete: saved pin for " + request.sourceRepository()
                    + " at " + request.sourceSHA() + ". " + message;
        }
        throw new IllegalArgumentException(message);
    }
}
