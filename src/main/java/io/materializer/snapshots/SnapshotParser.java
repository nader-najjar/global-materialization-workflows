package io.materializer.snapshots;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.google.inject.Inject;
import io.materializer.execution.InputSanitizer;
import io.materializer.execution.exceptions.InvalidInputException;
import io.materializer.shared.utilities.JsonMapperUtility;
import io.materializer.snapshots.models.LegacyPins;
import io.materializer.snapshots.models.PinSet;
import io.materializer.snapshots.models.SavedPins;
import io.materializer.snapshots.models.SnapshotHeader;
import io.materializer.snapshots.models.VersionOnePinSet;

public final class SnapshotParser {

    private final InputSanitizer sanitizer;

    @Inject
    public SnapshotParser(InputSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public SavedPins parse(String content) {
        Integer version = header(content).schemaVersion();
        if (version == null) {
            return sanitizer.parse(content, LegacyPins.class);
        }
        return switch (version) {
            case 1 -> sanitizer.parse(content, VersionOnePinSet.class);
            case 2 -> sanitizer.parse(content, PinSet.class);
            default -> throw new InvalidInputException("Unsupported snapshot schema version: " + version);
        };
    }

    private SnapshotHeader header(String content) {
        try {
            SnapshotHeader header = JsonMapperUtility.MAPPER.readerFor(SnapshotHeader.class)
                    .without(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).readValue(content);

            return sanitizer.validate(header);
        } catch (JsonProcessingException exception) {
            throw new InvalidInputException("Invalid snapshot JSON: " + exception.getOriginalMessage(), exception);
        }
    }
}
