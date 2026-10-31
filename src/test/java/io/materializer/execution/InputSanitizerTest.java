package io.materializer.execution;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.materializer.github.models.Execution;
import io.materializer.github.models.MaterializerDispatchResult;
import io.materializer.ignition.models.MaterializationRequest;
import io.materializer.ignition.models.SelectionMode;
import io.materializer.json.JSONDocuments;
import io.materializer.snapshots.SnapshotParser;
import io.materializer.snapshots.models.LegacyPins;
import io.materializer.snapshots.models.PinSet;
import io.materializer.snapshots.models.SoftwareDefinition;
import io.materializer.snapshots.models.VersionOnePinSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputSanitizerTest {

    private static final String HOST = "example/materializer";
    private static final String APP = "example/app";
    private static final String SHA = "a".repeat(40);

    private final InputSanitizer sanitizer = new InputSanitizer();

    private static ObjectNode snapshot() {
        return JSONDocuments.document("schema_version", 2, "software_definition_repository", HOST, "pins", Map.of(HOST, SHA));
    }

    private static ObjectNode source() {
        return JSONDocuments.document("source_repository", APP, "build_system", "universal", "runners", List.of("platform-a"));
    }

    @Test
    void typedSnapshotRetainsExplicitUninitializedPins() {
        var document = snapshot();
        document.withObject("pins").putNull(APP);
        var parsed = sanitizer.parse(JSONDocuments.compact(document), PinSet.class);
        assertEquals(SHA, parsed.softwareDefinitionSHA());
        assertTrue(parsed.pins().containsKey(APP));
        assertNull(parsed.pins().get(APP));
    }

    @Test
    void missingAndNullSnapshotFieldsFailAtTheBoundary() {
        for (String field : List.of("schema_version", "software_definition_repository", "pins")) {
            var missing = snapshot();
            missing.remove(field);
            assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(missing), PinSet.class));
            var explicitNull = snapshot();
            explicitNull.putNull(field);
            assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(explicitNull), PinSet.class));
        }
    }

    @Test
    void schemaVersionDoesNotAcceptConversionsOrUnsupportedVersions() {
        for (Object value : List.of("2", true, 2.0, 3, 2147483648L)) {
            var document = snapshot();
            document.set("schema_version", JSONDocuments.value(value));
            assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(document), PinSet.class));
        }
    }

    @Test
    void snapshotPinsHaveValidatedRepositoryKeysAndSHAs() {
        for (Object sha : List.of(true, 17, "main", "a".repeat(7), "A".repeat(40))) {
            var document = snapshot();
            document.withObject("pins").set(HOST, JSONDocuments.value(sha));
            assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(document), PinSet.class));
        }
        var document = snapshot();
        document.withObject("pins").put("invalid", SHA);
        assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(document), PinSet.class));
        document.withObject("pins").remove(HOST);
        assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(document), PinSet.class));
    }

    @Test
    void definitionDeserializesDirectlyIntoNestedRecords() {
        var definition = sanitizer.parse(JSONDocuments.compact(List.of(source())), SoftwareDefinition.class);
        assertEquals(APP, definition.sources().getFirst().sourceRepository());
        assertEquals(List.of("platform-a"), definition.sources().getFirst().runners());
        assertTrue(sanitizer.parse("[]", SoftwareDefinition.class).sources().isEmpty());
    }

    @Test
    void missingAndNullDefinitionFieldsFailAtTheBoundary() {
        for (String field : List.of("source_repository", "build_system", "runners")) {
            var missing = source();
            missing.remove(field);
            assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(List.of(missing)), SoftwareDefinition.class));
            var explicitNull = source();
            explicitNull.putNull(field);
            assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(List.of(explicitNull)), SoftwareDefinition.class));
        }
    }

    @Test
    void nestedValidationRejectsBadRunnersAndDuplicateRepositories() {
        for (Object runners : List.of(List.of(), List.of(" "), List.of("same", "same"), List.of(7), "platform-a")) {
            var document = source();
            document.set("runners", JSONDocuments.value(runners));
            assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(List.of(document)), SoftwareDefinition.class));
        }
        assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(List.of(source(), source())), SoftwareDefinition.class));
    }

    @Test
    void nestedValidationReportsTheFieldPath() {
        var document = source();
        document.put("build_system", " ");
        var failure = assertThrows(
                IllegalArgumentException.class,
                () -> sanitizer.parse(JSONDocuments.compact(List.of(document)), SoftwareDefinition.class)
        );

        assertTrue(failure.getMessage().contains("sources[0].buildSystem"), failure::getMessage);
    }

    @Test
    void textFieldsDoNotCoerceNumbersOrBooleans() {
        for (String field : List.of("source_repository", "build_system")) {
            for (Object value : List.of(123, false)) {
                var document = source();
                document.set(field, JSONDocuments.value(value));
                assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(List.of(document)), SoftwareDefinition.class));
            }
        }
    }

    @Test
    void exactProtocolSchemasRejectUnknownAndDuplicateFields() {
        var document = snapshot();
        document.put("unexpected", true);
        assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(document), PinSet.class));
        assertThrows(IllegalArgumentException.class, () -> sanitizer.parse("{\"pins\":{},\"pins\":{}}", PinSet.class));
        assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(snapshot()) + " {}", PinSet.class));
        assertThrows(IllegalArgumentException.class, () -> sanitizer.parse("null", PinSet.class));
    }

    @Test
    void githubResponsesIgnoreAdditionalMetadataWhileValidatingRequiredFields() {
        var result = sanitizer.parseResponse("{\"workflow_run_id\":700,\"future_field\":true}", MaterializerDispatchResult.class);
        assertEquals(700, result.runID());
        for (String input : List.of("{}", "{\"workflow_run_id\":null}", "{\"workflow_run_id\":true}",
                "{\"workflow_run_id\":\"700\"}", "{\"workflow_run_id\":700.1}", "{\"workflow_run_id\":0}",
                "{\"workflow_run_id\":9223372036854775808}")) {
            assertThrows(IllegalArgumentException.class, () -> sanitizer.parseResponse(input, MaterializerDispatchResult.class));
        }
    }

    @Test
    void selectionModeAndSourcePairAreValidatedTogether() {
        sanitizer.validate(new MaterializationRequest(SelectionMode.BOUNCE, null, null));
        sanitizer.validate(new MaterializationRequest(SelectionMode.MATERIALIZE, APP, SHA));
        sanitizer.validate(new MaterializationRequest(SelectionMode.DRY_RUN, HOST, SHA));
        for (MaterializationRequest request : List.of(
                new MaterializationRequest(null, APP, SHA),
                new MaterializationRequest(SelectionMode.BOUNCE, APP, SHA),
                new MaterializationRequest(SelectionMode.MATERIALIZE, APP, null),
                new MaterializationRequest(SelectionMode.DRY_RUN, null, SHA),
                new MaterializationRequest(SelectionMode.DRY_RUN, null, null)
        )) {
            assertThrows(IllegalArgumentException.class, () -> sanitizer.validate(request));
        }
        var document = JSONDocuments.document("selection_mode", "unknown", "source_repository", APP, "source_sha", SHA);
        assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(document), MaterializationRequest.class));
    }

    @Test
    void executionRecordValidatesPhaseRefAndChildRelationships() {
        var execution = JSONDocuments.document(
                "phase", "prepared", "launcher_run_id", 100, "launcher_attempt", 1,
                "snapshot_sha", SHA, "materializer_sha", SHA, "execution_ref", "pipeline-executions/100-1", "child_run_id", null
        );

        assertNull(sanitizer.parse(JSONDocuments.compact(execution), Execution.class).childRunID());
        for (String field : List.of("phase", "launcher_run_id", "launcher_attempt", "snapshot_sha", "materializer_sha", "execution_ref", "child_run_id")) {
            var missing = execution.deepCopy();
            missing.remove(field);
            assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(missing), Execution.class));
        }
        execution.put("phase", "running");
        assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(execution), Execution.class));
        execution.put("child_run_id", 700);
        assertEquals(700, sanitizer.parse(JSONDocuments.compact(execution), Execution.class).childRunID());
        execution.put("execution_ref", "pipeline-executions/other");
        assertThrows(IllegalArgumentException.class, () -> sanitizer.parse(JSONDocuments.compact(execution), Execution.class));
    }

    @Test
    void nullableExecutionFileStillRejectsIncompleteRecords() {
        assertTrue(sanitizer.parseOptional("null", Execution.class).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> sanitizer.parseOptional("{}", Execution.class));
        assertThrows(IllegalArgumentException.class, () -> sanitizer.parseOptional("null {}", Execution.class));
    }

    @Test
    void migrationReadsEachSavedFormatIntoItsOwnRecord() {
        var parser = new SnapshotParser(sanitizer);
        assertInstanceOf(PinSet.class, parser.parse(JSONDocuments.compact(snapshot())));
        assertInstanceOf(LegacyPins.class, parser.parse(JSONDocuments.compact(Map.of(APP, SHA))));
        var versionOne = JSONDocuments.document("schema_version", 1, "pins", Map.of(HOST, SHA, APP, SHA), "sources", List.of(source()));
        assertInstanceOf(VersionOnePinSet.class, parser.parse(JSONDocuments.compact(versionOne)));
        for (String input : List.of("null", "[]", "{\"schema_version\":true}", "{\"schema_version\":2.0}", "{\"schema_version\":\"2\"}")) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(input));
        }
    }
}
