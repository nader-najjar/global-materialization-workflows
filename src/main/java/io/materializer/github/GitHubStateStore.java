package io.materializer.github;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.google.inject.Inject;
import io.materializer.execution.InputSanitizer;
import io.materializer.github.models.CommitComparison;
import io.materializer.github.models.CommitSHA;
import io.materializer.github.models.CreateCommitRequest;
import io.materializer.github.models.CreateReferenceRequest;
import io.materializer.github.models.CreateTreeRequest;
import io.materializer.github.models.Execution;
import io.materializer.github.models.ExecutionIdentity;
import io.materializer.github.models.ExecutionPhase;
import io.materializer.github.models.GitCommit;
import io.materializer.github.models.GitHubFile;
import io.materializer.github.models.GitReference;
import io.materializer.github.models.GitTreeEntry;
import io.materializer.github.models.SavedState;
import io.materializer.github.models.UpdateReferenceRequest;
import io.materializer.shared.utilities.CanonicalJsonUtility;
import io.materializer.shared.utilities.Preconditions;
import io.materializer.snapshots.SnapshotParser;
import io.materializer.snapshots.models.PinSet;

public final class GitHubStateStore {

    public static final String BRANCH = "materialization-state";
    private final GitHubAPI client;
    private final Polling polling;
    private final InputSanitizer sanitizer;
    private final SnapshotParser snapshots;

    @Inject
    public GitHubStateStore(GitHubAPI client, Polling polling, InputSanitizer sanitizer, SnapshotParser snapshots) {
        this.client = client;
        this.polling = polling;
        this.sanitizer = sanitizer;
        this.snapshots = snapshots;
    }

    private static String objectSHA(GitReference response) {
        return response.object().sha();
    }

    private static String decodeFile(GitHubFile response) {
        byte[] bytes = Base64.getDecoder().decode(response.content().replaceAll("\\s+", ""));
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("GitHub files must use UTF-8", exception);
        }
    }

    public String readText(String path, String commitSHA) {
        return decodeFile(client.get("/contents/" + path + "?ref=" + commitSHA, GitHubFile.class));
    }

    public PinSet readSnapshot(String commitSHA) {
        return sanitizer.parse(readText("source-pins.json", commitSHA), PinSet.class);
    }

    public Execution readExecution(String commitSHA) {
        var response = client.getOptional("/contents/execution.json?ref=" + commitSHA, GitHubFile.class);
        if (response == null) {
            return null;
        }
        return sanitizer.parseOptional(decodeFile(response), Execution.class).orElse(null);
    }

    public SavedState load() {
        var branch = client.getOptional("/git/ref/heads/" + BRANCH, GitReference.class);
        if (branch == null) {
            return new SavedState(null, null, null);
        }
        String commitSHA = objectSHA(branch);
        var content = snapshots.parse(readText("source-pins.json", commitSHA));
        return new SavedState(content, commitSHA, readExecution(commitSHA));
    }

    private String createTree(Map<String, String> files, String previousSHA) {
        var entries = new TreeMap<>(files).entrySet().stream()
                .map(entry -> new GitTreeEntry(entry.getKey(), "100644", "blob", entry.getValue())).toList();

        String baseTree = previousSHA == null ? null : client.get("/git/commits/" + previousSHA, GitCommit.class).tree().sha();
        var tree = client.request("POST", "/git/trees", new CreateTreeRequest(entries, baseTree), CommitSHA.class);
        return tree.sha();
    }

    private String createCommit(Map<String, String> files, String previousSHA) {
        var parents = previousSHA == null ? List.<String>of() : List.of(previousSHA);
        var payload = new CreateCommitRequest("Update materialization state", createTree(files, previousSHA), parents);
        return client.request("POST", "/git/commits", payload, CommitSHA.class).sha();
    }

    public boolean stateBranchExists() {
        return client.getOptional("/git/ref/heads/" + BRANCH, GitReference.class) != null;
    }

    private String initializeStateBranch(Map<String, String> files) {
        String commitSHA = createCommit(files, null);
        var payload = new CreateReferenceRequest("refs/heads/" + BRANCH, commitSHA);
        var response = client.request("POST", "/git/refs", payload, GitReference.class);
        Preconditions.require(objectSHA(response).equals(commitSHA), "GitHub did not confirm the initial state commit");
        return commitSHA;
    }

    private String updateStateBranch(Map<String, String> files, String previousSHA) {
        String commitSHA = createCommit(files, previousSHA);
        var payload = new UpdateReferenceRequest(commitSHA, false);
        var response = client.request("PATCH", "/git/refs/heads/" + BRANCH, payload, GitReference.class);
        Preconditions.require(objectSHA(response).equals(commitSHA), "GitHub did not confirm the saved state commit");
        return commitSHA;
    }

    public SavedState save(PinSet snapshot, Execution execution, SavedState previous) {
        sanitizer.validate(snapshot);
        if (execution != null) {
            sanitizer.validate(execution);
        }
        var files = Map.of("source-pins.json", CanonicalJsonUtility.encode(snapshot), "execution.json", CanonicalJsonUtility.encode(execution));
        if (previous.commitSHA() == null) {
            Preconditions.require(!stateBranchExists(), "State branch appeared while initializing; submit a new request");
            return new SavedState(snapshot, initializeStateBranch(files), execution);
        }
        return new SavedState(snapshot, updateStateBranch(files, previous.commitSHA()), execution);
    }

    public String publishSnapshot(PinSet snapshot) {
        String content = CanonicalJsonUtility.encode(sanitizer.validate(snapshot));
        String ref = "pipeline-snapshots/" + digest(content);
        var existing = client.getOptional("/git/ref/tags/" + ref, GitReference.class);
        if (existing != null) {
            String commitSHA = objectSHA(existing);
            Preconditions.require(readSnapshot(commitSHA).equals(snapshot), "Snapshot ref content does not match its identity");
            return commitSHA;
        }
        String commitSHA = createCommit(Map.of("source-pins.json", content), null);
        publishExecutionRef(ref, commitSHA);
        return commitSHA;
    }

    private static String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public void publishExecutionRef(String ref, String commitSHA) {
        String encoded = URLEncoder.encode(ref, StandardCharsets.UTF_8).replace("%2F", "/").replace("+", "%20");
        var existing = client.getOptional("/git/ref/tags/" + encoded, GitReference.class);
        if (existing != null) {
            Preconditions.require(objectSHA(existing).equals(commitSHA), "Execution ref already points to another commit");
            return;
        }
        var payload = new CreateReferenceRequest("refs/tags/" + ref, commitSHA);
        var response = client.request("POST", "/git/refs", payload, GitReference.class);
        Preconditions.require(objectSHA(response).equals(commitSHA), "GitHub did not confirm the immutable ref");
    }

    private Execution ownedExecution(String snapshotSHA, ExecutionIdentity identity) {
        var branch = client.get("/git/ref/heads/" + BRANCH, GitReference.class);
        var execution = readExecution(objectSHA(branch));
        Preconditions.require(execution != null, "No launcher owns this execution");
        Preconditions.require(execution.snapshotSHA().equals(snapshotSHA), "Snapshot is not owned by the active launcher");
        Preconditions.require(identity.ref().equals("refs/tags/" + execution.executionRef()), "Execution was not launched at its immutable ref");
        Preconditions.require(identity.sha().equals(execution.materializerSHA()), "Execution SHA does not match its owner");
        if (execution.phase() == ExecutionPhase.RUNNING) {
            Preconditions.require(Long.valueOf(identity.runID()).equals(execution.childRunID()), "Another run owns this execution");
            return execution;
        }
        Preconditions.require(execution.phase() == ExecutionPhase.DISPATCHING, "Execution has not been dispatched");
        return null;
    }

    public void authorizeExecution(String snapshotSHA, ExecutionIdentity identity) {
        polling.await(
                () -> ownedExecution(snapshotSHA, identity),
                Duration.ofSeconds(300),
                Duration.ofSeconds(5),
                "Launcher has not confirmed this child run; submit a new materializer request to recover it"
        );
    }

    public void validateMaterializerSuccessor(String activeSHA, String candidateSHA) {
        if (activeSHA.equals(candidateSHA)) {
            return;
        }
        var comparison = client.get("/compare/" + activeSHA + "..." + candidateSHA, CommitComparison.class);
        Preconditions.require(
                Set.of("ahead", "identical").contains(comparison.status()),
                "Automatic materializer updates must descend from its active pin; use a manual request for a rollback"
        );
    }
}
