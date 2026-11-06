package io.materializer.ignition.models;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.materializer.shared.utilities.HibernateValidatorUtility;
import io.materializer.shared.utilities.JsonMapperUtility;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnapshotTest {

    private final RepositoryRevision pipeline = new RepositoryRevision("owner/pipeline", "a".repeat(40));
    private final RepositoryRevision service = new RepositoryRevision("owner/service", "b".repeat(40));
    private final RepositoryRevision source = new RepositoryRevision("owner/source", "c".repeat(40));

    @Test
    void preservesAllPinnedRevisionsThroughJSONRoundTrip() throws IOException {
        Snapshot original = new Snapshot(pipeline, service, Map.of("application", source));
        String json = JsonMapperUtility.MAPPER.writeValueAsString(original);
        Snapshot restored = JsonMapperUtility.MAPPER.readValue(json, Snapshot.class);
        HibernateValidatorUtility.validate(restored);

        assertEquals(original, restored);
        assertEquals("c".repeat(40), restored.repositories().get("application").commitSHA());
    }

    @Test
    void freezesRepositoryMappingsWithoutLosingOrder() {
        Map<String, RepositoryRevision> repositories = new LinkedHashMap<>();
        repositories.put("first", source);
        repositories.put("second", pipeline);
        Snapshot snapshot = new Snapshot(pipeline, service, repositories);
        repositories.clear();

        assertEquals(List.of("first", "second"), new ArrayList<>(snapshot.repositories().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.repositories().clear());
    }

    @Test
    void validatesRequiredSnapshotFields() {
        assertThrows(
                ValidationException.class,
                () -> HibernateValidatorUtility.validate(new Snapshot(null, service, Map.of()))
        );

        assertThrows(
                ValidationException.class,
                () -> HibernateValidatorUtility.validate(new Snapshot(pipeline, null, Map.of()))
        );

        assertThrows(
                ValidationException.class,
                () -> HibernateValidatorUtility.validate(new Snapshot(pipeline, service, null))
        );
    }

    @Test
    void rejectsInvalidNestedRepositoryRevisions() {
        RepositoryRevision invalid = new RepositoryRevision("missing-owner", "main");
        Snapshot snapshot = new Snapshot(pipeline, service, Map.of("application", invalid));

        assertThrows(ValidationException.class, () -> HibernateValidatorUtility.validate(snapshot));
    }

    @Test
    void rejectsNullNamedRevisions() {
        Map<String, RepositoryRevision> repositories = new HashMap<>();
        repositories.put("application", null);
        Snapshot snapshot = new Snapshot(pipeline, service, repositories);

        assertThrows(ValidationException.class, () -> HibernateValidatorUtility.validate(snapshot));
    }

    @Test
    void rejectsBlankRepositoryNames() {
        Snapshot snapshot = new Snapshot(pipeline, service, Map.of(" ", source));

        assertThrows(ValidationException.class, () -> HibernateValidatorUtility.validate(snapshot));
    }
}
