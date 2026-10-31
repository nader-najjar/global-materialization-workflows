package io.materializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.materializer.builds.BuildMatrix;
import io.materializer.environment.EnvironmentVariablesFactory;
import io.materializer.execution.InputSanitizer;
import io.materializer.github.GitHubStateStore;
import io.materializer.github.Polling;
import io.materializer.github.exceptions.GitHubRequestException;
import io.materializer.github.models.CommitSHA;
import io.materializer.github.models.Execution;
import io.materializer.github.models.ExecutionPhase;
import io.materializer.json.JSONDocuments;
import io.materializer.snapshots.SnapshotParser;
import io.materializer.snapshots.models.PinSet;
import io.materializer.snapshots.models.SoftwareDefinition;
import io.materializer.snapshots.models.SourceDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static io.materializer.FakeGitHub.APP;
import static io.materializer.FakeGitHub.APP_NEW;
import static io.materializer.FakeGitHub.APP_OLD;
import static io.materializer.FakeGitHub.HOST;
import static io.materializer.FakeGitHub.NEW;
import static io.materializer.FakeGitHub.OLD;
import static io.materializer.FakeGitHub.OTHER;
import static io.materializer.FakeGitHub.OTHER_SHA;
import static io.materializer.FakeGitHub.registry;
import static io.materializer.FakeGitHub.registryWithRunners;
import static io.materializer.FakeGitHub.snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IgnitionBehaviorTest {

    @TempDir
    Path directory;
    private Map<String, String> variables;
    private FakeGitHub api;
    private Polling polling;
    private TestApplications applications;

    @BeforeEach
    void setUp() {
        variables = new HashMap<>(Map.of("GITHUB_REPOSITORY", HOST, "GITHUB_API_URL", "https://api.invalid",
                "GITHUB_SERVER_URL", "https://github.invalid", "GH_TOKEN", "offline-token",
                "GITHUB_EVENT_NAME", "workflow_dispatch", "GITHUB_RUN_ID", "100", "GITHUB_RUN_ATTEMPT", "1",
                "GITHUB_RUN_NUMBER", "1", "GITHUB_SHA", OLD, "GITHUB_REF", "refs/heads/main"));

        variables.putAll(Map.of("GITHUB_WORKFLOW_REF", HOST + "/.github/workflows/ignition.yml@refs/heads/main",
                "GITHUB_WORKFLOW_SHA", OLD, "GITHUB_WORKSPACE", directory.toString(),
                "GITHUB_OUTPUT", directory.resolve("output").toString(),
                "GITHUB_STEP_SUMMARY", directory.resolve("summary").toString(),
                "IMPLEMENTATION_REPOSITORY", "example/shared-workflows", "IMPLEMENTATION_REF", OLD,
                "SELECTION_PATH", directory.resolve("selection.json").toString(), "SELECTION_MODE", "materialize",
                "SOURCE_REPOSITORY", APP));

        variables.put("SOURCE_SHA", APP_NEW);
        api = new FakeGitHub();
        var clock = new AtomicLong();
        polling = new Polling(clock::get, duration -> clock.addAndGet(duration.toNanos()));
        applications = new TestApplications(api, polling);
    }

    private GitHubStateStore stateStore() {
        return new GitHubStateStore(api, polling, new InputSanitizer(), new SnapshotParser(new InputSanitizer()));
    }

    private ObjectNode execute() throws IOException {
        return execute("materialize", APP, APP_NEW);
    }

    private ObjectNode execute(String mode, String repository, String sha) throws IOException {
        variables.putAll(Map.of("SELECTION_MODE", mode, "SOURCE_REPOSITORY", repository, "SOURCE_SHA", sha));
        applications.ignite(variables);
        return JSONDocuments.object(JSONDocuments.parse(Files.readString(directory.resolve("selection.json"),
                StandardCharsets.UTF_8)), "selection");
    }

    private Execution pending(ExecutionPhase phase, Long child) {
        String pinned = stateStore().publishSnapshot(pinSet(snapshot()));
        var execution = new Execution(phase, 99, 1, pinned, OLD, "pipeline-executions/99-1",
                phase == ExecutionPhase.RUNNING ? child : null);

        api.install(snapshot(), execution);
        if (phase != ExecutionPhase.PREPARED && child != null) {
            api.runs.put(child, api.run(execution, child));
        }
        return execution;
    }

    private Execution pending() {
        return pending(ExecutionPhase.RUNNING, 600L);
    }

    private static PinSet pinSet(JsonNode document) {
        return new InputSanitizer().parse(JSONDocuments.compact(document), PinSet.class);
    }

    private static List<SourceDefinition> readDefinition(String content) {
        return new InputSanitizer().parse(content, SoftwareDefinition.class).sources();
    }

    private static void fails(String message, Executable action) {
        var failure = assertThrows(RuntimeException.class, action);
        assertTrue(failure.getMessage().contains(message), failure::getMessage);
    }

    private static void sameDocument(Object expected, Object actual) {
        assertEquals(JSONDocuments.encode(expected), JSONDocuments.encode(actual));
    }

    @Test
    void materializeSavesCandidateAndDispatchesExactWorkflowAndManifest() throws IOException {
        var record = execute();
        assertEquals(APP_NEW, api.state().path("pins").path(APP).asText());
        assertNull(api.execution());
        var ownership = JSONDocuments.object(record.get("execution"), "execution").deepCopy();
        ownership.remove("conclusion");
        var child = new InputSanitizer().parse(JSONDocuments.compact(ownership), Execution.class);
        assertEquals(OLD, api.refs.get("tags/" + child.executionRef()));
        sameDocument(JSONDocuments.document("snapshot_sha", child.snapshotSHA()), api.dispatches.getFirst().get("inputs"));
        sameDocument(stateStore().readSnapshot(child.snapshotSHA()), api.state());
        assertFalse(api.state().has("sources"));
    }

    @Test
    void dryRunPreservesActivePinsAndFreezesOverride() throws IOException {
        var original = api.state();
        var record = execute("dry_run", APP, APP_NEW);
        sameDocument(original, api.state());
        var frozen = stateStore().readSnapshot(record.path("execution").path("snapshot_sha").asText());
        assertEquals(APP_NEW, frozen.pins().get(APP));
        assertEquals(OLD, frozen.pins().get(HOST));
    }

    @Test
    void bounceReusesIdenticalLogicalSnapshot() throws IOException {
        var original = api.state();
        var first = execute("bounce", "", "");
        variables.put("GITHUB_RUN_ID", "101");
        var second = execute("bounce", "", "");
        sameDocument(original, api.state());
        assertEquals(first.path("execution").path("snapshot_sha"), second.path("execution").path("snapshot_sha"));
    }

    @Test
    void definitionUpdatePrunesRemovedPins() throws IOException {
        api.install(snapshot(Map.of(HOST, OLD, APP, APP_OLD, OTHER, OTHER_SHA)), null);
        var record = execute("materialize", HOST, NEW);
        sameDocument(JSONDocuments.value(Map.of(HOST, NEW, APP, APP_OLD)), api.state().get("pins"));
        assertEquals(NEW, record.path("execution").path("materializer_sha").asText());
    }

    @Test
    void definitionAdditionSavesIncompleteProgressWithoutDispatch() throws IOException {
        api.define(NEW, registry(APP, OTHER));
        fails("saved pin", () -> execute("materialize", HOST, NEW));
        assertEquals(NEW, api.state().path("pins").path(HOST).asText());
        assertTrue(api.state().path("pins").path(OTHER).isNull());
        assertTrue(api.dispatches.isEmpty());
        execute("materialize", OTHER, OTHER_SHA);
        assertEquals(1, api.dispatches.size());
        assertEquals(APP_OLD, api.state().path("pins").path(APP).asText());
    }

    @Test
    void definitionDryRunUsesCandidateYAMLAndKeepsActiveDefinition() throws IOException {
        api.define(NEW, registryWithRunners(List.of("different-platform"), APP));
        var record = execute("dry_run", HOST, NEW);
        assertEquals(OLD, api.state().path("pins").path(HOST).asText());
        assertEquals(NEW, record.path("execution").path("materializer_sha").asText());
        var frozen = pinSet(record.get("candidate"));
        var sources = readDefinition(stateStore().readText(".github/repositories.json", NEW));
        assertEquals(List.of("different-platform"), BuildMatrix.generate(frozen, sources).stream().map(build -> build.runner()).toList());
    }

    @Test
    void incompleteDefinitionDryRunDoesNotWriteActiveState() {
        api.define(NEW, registry(APP, OTHER));
        String before = api.refs.get("heads/materialization-state");
        fails("not initialized", () -> execute("dry_run", HOST, NEW));
        assertEquals(before, api.refs.get("heads/materialization-state"));
        assertTrue(api.dispatches.isEmpty());
    }

    @Test
    void firstMaterializerRequestInitializesThenSourceFillsPin() throws IOException {
        api.refs.remove("heads/materialization-state");
        fails("saved pin", () -> execute("materialize", HOST, OLD));
        var commit = api.commits.get(api.refs.get("heads/materialization-state"));
        assertTrue(commit.path("parents").isEmpty());
        assertEquals(HOST, api.state().path("software_definition_repository").asText());
        assertTrue(api.state().path("pins").path(APP).isNull());
        execute();
        assertEquals(1, api.dispatches.size());
    }

    @Test
    void sourceRequestCannotInferFirstDefinition() {
        api.refs.remove("heads/materialization-state");
        fails("initialize", this::execute);
        assertFalse(api.refs.containsKey("heads/materialization-state"));
    }

    @Test
    void legacyPinsRequireExplicitMaterializerMigration() throws IOException {
        api.install(JSONDocuments.document(APP, APP_OLD), null);
        fails("initialize", () -> execute("bounce", "", ""));
        execute("materialize", HOST, NEW);
        sameDocument(JSONDocuments.value(Map.of(HOST, NEW, APP, APP_OLD)), api.state().get("pins"));
    }

    @Test
    void versionOneMetadataIsReplacedByPinnedDefinition() throws IOException {
        api.install(JSONDocuments.document("schema_version", 1, "pins", Map.of(HOST, OLD, APP, APP_OLD), "sources", registry(APP)), null);
        execute("materialize", HOST, NEW);
        sameDocument(snapshot(Map.of(HOST, NEW, APP, APP_OLD)), api.state());
    }

    @Test
    void unknownSourceIsRejectedBeforeStatePublication() {
        String before = api.refs.get("heads/materialization-state");
        fails("not registered", () -> execute("materialize", OTHER, OTHER_SHA));
        assertEquals(before, api.refs.get("heads/materialization-state"));
    }

    @Test
    void failureKeepsDesiredPinsAndReleasesFinishedChild() {
        api.conclusion = "failure";
        fails("failure", this::execute);
        assertEquals(APP_NEW, api.state().path("pins").path(APP).asText());
        assertNull(api.execution());
    }

    @Test
    void preparedButUnsentRequestCanBeRecovered() throws IOException {
        pending(ExecutionPhase.PREPARED, 600L);
        execute();
        assertEquals(1, api.dispatches.size());
        assertFalse(api.calls.stream().anyMatch(call -> call.path().contains("/actions/runs/600")));
    }

    @Test
    void orphanChildFinishesBeforeNextRequestDispatch() throws IOException {
        pending();
        api.statuses.put(600L, new ArrayDeque<>(List.of("in_progress", "completed")));
        execute();
        var paths = api.calls.stream().map(FakeGitHub.Call::path).toList();
        assertTrue(paths.lastIndexOf("/actions/runs/600") < paths.indexOf("/actions/workflows/materializer.yml/dispatches"));
        assertEquals(1, api.dispatches.size());
    }

    @Test
    void uncertainDispatchReconcilesExactChildWithoutRedispatch() throws IOException {
        pending(ExecutionPhase.DISPATCHING, 600L);
        execute();
        assertEquals(1, api.dispatches.size());
        assertTrue(api.calls.stream().anyMatch(call -> call.path().contains("/runs?")));
    }

    @Test
    void unresolvedDispatchKeepsOwnershipAndBlocksNewSelection() {
        var pending = pending(ExecutionPhase.DISPATCHING, null);
        var before = api.state();
        fails("uncertain", this::execute);
        assertEquals(pending, api.execution());
        sameDocument(before, api.state());
        assertTrue(api.dispatches.isEmpty());
    }

    @Test
    void duplicateDispatchMatchesKeepOwnership() {
        var pending = pending(ExecutionPhase.DISPATCHING, 600L);
        api.runs.put(601L, api.run(pending, 601));
        fails("found 2", this::execute);
        assertEquals(pending, api.execution());
        assertTrue(api.dispatches.isEmpty());
    }

    @Test
    void acceptedDispatchWithLostResponseIsRecovered() throws IOException {
        api.dispatchFailure = new IllegalStateException("response lost");
        fails("response lost", this::execute);
        String oldRef = api.execution().executionRef();
        assertEquals(ExecutionPhase.DISPATCHING, api.execution().phase());
        api.dispatchFailure = null;
        variables.put("GITHUB_RUN_ID", "101");
        execute("bounce", "", "");
        assertEquals(2, api.dispatches.size());
        assertEquals(1, api.dispatches.stream().filter(dispatch -> dispatch.path("ref").asText().equals(oldRef)).count());
    }

    @Test
    void definitivelyRejectedDispatchReleasesOwnershipWithoutRollback() throws IOException {
        api.rejectedDispatchStatus = 422;
        assertThrows(GitHubRequestException.class, this::execute);
        assertNull(api.execution());
        assertEquals(APP_NEW, api.state().path("pins").path(APP).asText());
        assertTrue(api.dispatches.isEmpty());
        api.rejectedDispatchStatus = null;
        variables.put("GITHUB_RUN_ID", "101");
        execute("bounce", "", "");
        assertEquals(1, api.dispatches.size());
    }

    @Test
    void serverErrorKeepsDispatchOwnershipUntilReconciled() {
        api.rejectedDispatchStatus = 503;
        assertThrows(GitHubRequestException.class, this::execute);
        assertEquals(ExecutionPhase.DISPATCHING, api.execution().phase());
        assertEquals(APP_NEW, api.state().path("pins").path(APP).asText());
    }

    @Test
    void mainAdvancingDoesNotChangePinnedDefinition() throws IOException {
        api.define(NEW, registry(OTHER));
        variables.put("GITHUB_SHA", NEW);
        var record = execute();
        assertEquals(OLD, record.path("execution").path("materializer_sha").asText());
        sameDocument(JSONDocuments.value(Map.of(HOST, OLD, APP, APP_NEW)), api.state().get("pins"));
    }

    @Test
    void launcherRerunDoesNotDispatchAgain() {
        variables.put("GITHUB_RUN_ATTEMPT", "2");
        fails("rerunning", this::execute);
        assertTrue(api.dispatches.isEmpty());
    }

    @Test
    void staleStateWriteDoesNotOverwriteNewerState() {
        var state = stateStore();
        var before = state.load();
        var newer = snapshot(Map.of(HOST, OLD, APP, APP_NEW));
        state.save(pinSet(newer), null, before);
        fails("compare-and-swap", () -> state.save(pinSet(snapshot()), null, before));
        sameDocument(newer, api.state());
    }

    @Test
    void snapshotTagCollisionCannotSelectOtherPins() {
        var state = stateStore();
        String sha = state.publishSnapshot(pinSet(snapshot()));
        api.files(sha).put("source-pins.json", JSONDocuments.encode(snapshot(Map.of(HOST, OLD, APP, APP_NEW))));
        fails("does not match", () -> state.publishSnapshot(pinSet(snapshot())));
    }

    @Test
    void executionTagCannotBeMoved() {
        var state = stateStore();
        state.publishExecutionRef("pipeline-executions/100-1", OLD);
        fails("another commit", () -> state.publishExecutionRef("pipeline-executions/100-1", NEW));
    }

    @Test
    void executionRequiresLiveExactOwnership() {
        var pending = pending();
        variables.putAll(Map.of("GITHUB_RUN_ID", "600", "GITHUB_REF", "refs/tags/" + pending.executionRef()));
        stateStore().authorizeExecution(pending.snapshotSHA(), EnvironmentVariablesFactory.identity(variables));
        variables.put("GITHUB_RUN_ID", "601");
        fails("Another run", () -> stateStore().authorizeExecution(pending.snapshotSHA(), EnvironmentVariablesFactory.identity(variables)));
    }

    @Test
    void executionCannotUseDifferentSnapshotOrCode() {
        var pending = pending();
        variables.putAll(Map.of("GITHUB_RUN_ID", "600", "GITHUB_REF", "refs/tags/" + pending.executionRef()));
        fails("Snapshot is not owned", () -> stateStore().authorizeExecution(NEW, EnvironmentVariablesFactory.identity(variables)));
        variables.put("GITHUB_SHA", NEW);
        fails("SHA does not match", () -> stateStore().authorizeExecution(pending.snapshotSHA(), EnvironmentVariablesFactory.identity(variables)));
    }

    @Test
    void emptyDefinitionHasValidEmptyMatrix() throws IOException {
        api.define(NEW, JSONDocuments.arrayOf(List.of()));
        execute("materialize", HOST, NEW);
        assertTrue(BuildMatrix.generate(pinSet(api.state()), List.of()).isEmpty());
    }

    @Test
    void automaticDefinitionRollbackIsRejectedButManualIsAllowed() throws IOException {
        variables.put("GITHUB_EVENT_NAME", "push");
        fails("must descend", () -> execute("materialize", HOST, NEW));
        variables.put("GITHUB_EVENT_NAME", "workflow_dispatch");
        execute("materialize", HOST, NEW);
    }

    @Test
    void duplicateJSONFieldsAndInvalidSHAsAreRejected() {
        fails("Duplicate", () -> JSONDocuments.parse("{\"pins\":{},\"pins\":{}}"));
        for (String invalid : List.of("main", "a".repeat(7), "A".repeat(40))) {
            assertThrows(IllegalArgumentException.class, () -> new InputSanitizer().validate(new CommitSHA(invalid)));
        }
        assertThrows(IllegalArgumentException.class, () -> new InputSanitizer().validate(new CommitSHA(null)));
        assertThrows(IllegalArgumentException.class, () -> pinSet(JSONDocuments.parse(
                "{\"schema_version\":2,\"software_definition_repository\":\"example/materializer\",\"pins\":{\"example/materializer\":true}}")));
    }

    @Test
    void childMatrixReadsFrozenSnapshotInsteadOfCurrentSavedPins() throws IOException {
        var pending = pending();
        api.install(snapshot(Map.of(HOST, OLD, APP, APP_NEW)), pending);
        childVariables(pending);
        var definition = directory.resolve(".github/repositories.json");
        Files.createDirectories(directory.resolve(".github"));
        Files.writeString(definition, JSONDocuments.compact(registry(APP)), StandardCharsets.UTF_8);
        applications.matrix(variables);
        var record = JSONDocuments.parse(Files.readString(directory.resolve("selection.json"), StandardCharsets.UTF_8));
        var builds = JSONDocuments.array(record.get("builds"), "builds");
        assertEquals(List.of(APP_OLD, APP_OLD), builds.valueStream().map(build -> build.path("source_sha").asText()).toList());
        assertEquals(List.of("platform-a", "platform-b"), builds.valueStream().map(build -> build.path("runner").asText()).toList());
    }

    private void childVariables(Execution pending) {
        variables.putAll(Map.of("GITHUB_RUN_ID", "600", "GITHUB_REF", "refs/tags/" + pending.executionRef(),
                "SNAPSHOT_SHA", pending.snapshotSHA()));
    }

    @Test
    void childRerunFailsBeforeBuildMatrixGeneration() {
        variables.putAll(Map.of("GITHUB_RUN_ATTEMPT", "2", "SNAPSHOT_SHA", OLD));
        fails("rerunning", () -> applications.matrix(variables));
        assertFalse(Files.exists(directory.resolve("output")));
    }

    @Test
    void wrongChildWorkflowIdentityLeavesOwnershipHeld() {
        var pending = pending();
        api.runs.get(600L).put("head_sha", NEW);
        fails("SHA changed", this::execute);
        assertEquals(pending, api.execution());
        assertTrue(api.dispatches.isEmpty());
    }

    @Test
    void failedPreviousChildDoesNotRollbackAcceptedPins() throws IOException {
        pending();
        api.runs.get(600L).put("conclusion", "failure");
        execute();
        assertEquals(APP_NEW, api.state().path("pins").path(APP).asText());
        assertNull(api.execution());
    }

    @Test
    void readdingRemovedSourceDoesNotResurrectOldPin() throws IOException {
        api.define(NEW, JSONDocuments.arrayOf(List.of()));
        execute("materialize", HOST, NEW);
        variables.put("GITHUB_RUN_ID", "101");
        api.define(OLD, registry(APP));
        fails("saved pin", () -> execute("materialize", HOST, OLD));
        assertTrue(api.state().path("pins").path(APP).isNull());
    }

    @Test
    void missingPinsInExistingStateBranchIsNotSilentlyInitialized() {
        api.files(api.refs.get("heads/materialization-state")).remove("source-pins.json");
        var failure = assertThrows(GitHubRequestException.class, () -> execute("materialize", HOST, NEW));
        assertEquals(404, failure.status());
        assertTrue(api.dispatches.isEmpty());
    }

    @Test
    void materializeDispatchReturnsWithoutWaitingForCentralRun() {
        variables.put("MATERIALIZER_REPOSITORY", HOST);
        applications.dispatch(variables);
        assertFalse(api.calls.stream().anyMatch(call -> call.path().startsWith("/actions/runs/")));
        assertEquals("main", api.dispatches.getFirst().path("ref").asText());
        sameDocument(JSONDocuments.document("selection_mode", "materialize", "source_repository", APP,
                "source_sha", APP_NEW), api.dispatches.getFirst().get("inputs"));
    }

    @Test
    void dryRunDispatchWaitsForExactCentralRunAndPropagatesFailure() {
        variables.putAll(Map.of("MATERIALIZER_REPOSITORY", HOST, "SELECTION_MODE", "dry_run"));
        api.conclusion = "failure";
        fails("Central dry run 800 finished with: failure", () -> applications.dispatch(variables));
        assertTrue(api.calls.stream().anyMatch(call -> call.path().equals("/actions/runs/800")));
    }

    @Test
    void snapshotSerializationPreservesPythonCanonicalBytesAndIdentity() {
        String expected = "{\n  \"pins\": {\n    \"example/app\": \"" + APP_OLD
                + "\",\n    \"example/materializer\": \"" + OLD + "\"\n  },\n  \"schema_version\": 2,\n"
                + "  \"software_definition_repository\": \"example/materializer\"\n}\n";
        assertEquals(expected, JSONDocuments.encode(snapshot()));
        String first = stateStore().publishSnapshot(pinSet(snapshot()));
        String second = stateStore().publishSnapshot(pinSet(snapshot()));
        assertEquals(first, second);
        assertNotEquals(OLD, first);
        assertEquals(first, api.refs.get("tags/pipeline-snapshots/93ef0cb5625f2a10dc2a4fdfd17ec39a18185595389c7217de42795fc991fbfb"));
    }
}
