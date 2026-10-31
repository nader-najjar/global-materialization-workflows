package io.materializer.snapshots.models;

import java.util.Map;

public sealed interface SavedPins permits PinSet, LegacyPins, VersionOnePinSet {

    Map<String, String> pins();
}
