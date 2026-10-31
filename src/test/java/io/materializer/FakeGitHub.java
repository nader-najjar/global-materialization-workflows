package io.materializer;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.materializer.execution.InputSanitizer;
import io.materializer.github.GitHubAPI;
import io.materializer.github.exceptions.GitHubRequestException;
import io.materializer.github.models.Execution;
import io.materializer.github.models.ExecutionPhase;
import io.materializer.github.models.GitHubRequest;
import io.materializer.github.models.GitHubResponse;
import io.materializer.json.JSONDocuments;
import io.materializer.shared.utilities.Preconditions;

final class FakeGitHub implements GitHubAPI {

    static final String HOST = "example/materializer";
    static final String APP = "example/app";
    static final String OTHER = "example/other";
    static final String OLD = "a".repeat(40);
    static final String NEW = "b".repeat(40);
    static final String APP_OLD = "c".repeat(40);
    static final String APP_NEW = "d".repeat(40);
    static final String OTHER_SHA = "e".repeat(40);

    record Call(String method, String path, ObjectNode payload) { }

    final Map<String, Map<String, String>> trees = new HashMap<>();
    final Map<String, ObjectNode> commits = new HashMap<>();
    final Map<String, String> refs = new HashMap<>();
    final Map<Long, ObjectNode> runs = new LinkedHashMap<>();
    final List<Call> calls = new ArrayList<>();
    final List<ObjectNode> dispatches = new ArrayList<>();
    final Map<Long, ArrayDeque<String>> statuses = new HashMap<>();
    RuntimeException dispatchFailure;
    Integer rejectedDispatchStatus;
    String conclusion = "success";
    private int counter;

    FakeGitHub() {
        define(OLD, registry(APP));
        define(NEW, registry(APP));
        install(snapshot(), null);
    }

    static JsonNode registry(String... names) {
        return registryWithRunners(List.of("platform-a", "platform-b"), names);
    }

    static JsonNode registryWithRunners(List<String> runners, String... names) {
        return JSONDocuments.arrayOf(Arrays.stream(names).map(name -> JSONDocuments.document(
                "source_repository", name, "build_system", "universal", "runners", runners)).toList());
    }

    static ObjectNode snapshot() {
        return snapshot(Map.of(HOST, OLD, APP, APP_OLD));
    }

    static ObjectNode snapshot(Map<String, String> pins) {
        return JSONDocuments.document("schema_version", 2, "software_definition_repository", HOST, "pins", pins);
    }

    private String identity(Object value) {
        try {
            String content = ++counter + JSONDocuments.compact(JSONDocuments.value(value));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    void define(String sha, JsonNode sources) {
        String tree = identity(sources);
        trees.put(tree, new HashMap<>(Map.of(".github/repositories.json", JSONDocuments.compact(sources),
                ".github/workflows/materializer.yml", "name: Materializer\non: workflow_dispatch\n")));

        commits.put(sha, JSONDocuments.document("sha", sha, "tree", JSONDocuments.document("sha", tree), "parents", List.of()));
    }

    String install(ObjectNode document, Execution execution) {
        String tree = identity(document);
        trees.put(tree, new HashMap<>(Map.of("source-pins.json", JSONDocuments.encode(document),
                "execution.json", JSONDocuments.encode(execution))));

        String sha = identity(tree);
        commits.put(sha, JSONDocuments.document("sha", sha, "tree", JSONDocuments.document("sha", tree), "parents", List.of()));
        refs.put("heads/materialization-state", sha);
        return sha;
    }

    Map<String, String> files(String commit) {
        return trees.get(commits.get(commit).path("tree").path("sha").asText());
    }

    ObjectNode state() {
        return JSONDocuments.object(JSONDocuments.parse(files(refs.get("heads/materialization-state"))
                .get("source-pins.json")), "state");
    }

    Execution execution() {
        var value = JSONDocuments.parse(files(refs.get("heads/materialization-state")).get("execution.json"));
        return value.isNull() ? null : new InputSanitizer().parse(JSONDocuments.compact(value), Execution.class);
    }

    ObjectNode run(Execution execution, long runID) {
        return JSONDocuments.document("id", runID, "event", "workflow_dispatch", "head_sha", execution.materializerSHA(),
                "head_branch", execution.executionRef(), "path", ".github/workflows/materializer.yml",
                "status", "completed", "conclusion", conclusion, "run_attempt", 1);
    }

    @Override
    public <T extends GitHubResponse> T request(String method, String path, GitHubRequest request, Class<T> responseType) {
        var sanitizer = new InputSanitizer();
        ObjectNode payload = request == null ? null : JSONDocuments.object(JSONDocuments.value(sanitizer.validate(request)), "request");
        ObjectNode response = exchange(method, path, payload);
        return sanitizer.parseResponse(JSONDocuments.compact(response), responseType);
    }

    private ObjectNode exchange(String method, String path, ObjectNode payload) {
        calls.add(new Call(method, path, payload == null ? null : payload.deepCopy()));
        return switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path, payload);
            case "PATCH" -> updateRef(URI.create(path).getPath(), payload);
            default -> throw new AssertionError(method + " " + path);
        };
    }

    private ObjectNode get(String path) {
        var uri = URI.create(path);
        String route = uri.getPath();
        if (route.startsWith("/git/ref/")) {
            return readRef(path, route);
        }
        if (route.startsWith("/contents/")) {
            return readFile(path, route, query(uri));
        }
        if (route.startsWith("/git/commits/")) {
            return commits.get(route.substring("/git/commits/".length())).deepCopy();
        }
        if (route.startsWith("/compare/")) {
            return JSONDocuments.document("status", "behind");
        }
        if (route.equals("/actions/workflows/materializer.yml/runs")) {
            return workflowRuns(query(uri));
        }
        if (route.startsWith("/actions/runs/")) {
            return readRun(route);
        }
        throw new AssertionError("GET " + path);
    }

    private ObjectNode post(String path, ObjectNode payload) {
        return switch (URI.create(path).getPath()) {
            case "/git/trees" -> createTree(payload);
            case "/git/commits" -> createCommit(payload);
            case "/git/refs" -> createRef(payload);
            case "/actions/workflows/materializer.yml/dispatches" -> dispatch(payload);
            case "/actions/workflows/ignition.yml/dispatches" -> dispatchIgnition(payload);
            default -> throw new AssertionError("POST " + path);
        };
    }

    private static Map<String, String> query(URI uri) {
        var query = new HashMap<String, String>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            query.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return query;
    }

    private ObjectNode readRef(String path, String route) {
        String ref = route.substring("/git/ref/".length());
        if (!refs.containsKey(ref)) {
            throw new GitHubRequestException(404, "GET", path);
        }
        return refDocument(refs.get(ref));
    }

    private static ObjectNode refDocument(String sha) {
        return JSONDocuments.document("object", JSONDocuments.document("type", "commit", "sha", sha));
    }

    private ObjectNode readFile(String path, String route, Map<String, String> query) {
        String relative = route.substring("/contents/".length());
        var files = files(query.get("ref"));
        if (!files.containsKey(relative)) {
            throw new GitHubRequestException(404, "GET", path);
        }
        return JSONDocuments.document("type", "file", "encoding", "base64", "content", java.util.Base64.getEncoder()
                .encodeToString(files.get(relative).getBytes(StandardCharsets.UTF_8)));
    }

    private ObjectNode createTree(ObjectNode payload) {
        var files = new HashMap<String, String>();
        if (payload.has("base_tree")) {
            files.putAll(trees.get(payload.path("base_tree").asText()));
        }
        payload.path("tree").forEach(entry -> files.put(entry.path("path").asText(), entry.path("content").asText()));
        String sha = identity(files);
        trees.put(sha, files);
        return JSONDocuments.document("sha", sha);
    }

    private ObjectNode createCommit(ObjectNode payload) {
        String sha = identity(payload);
        var document = JSONDocuments.document("sha", sha, "tree", JSONDocuments.document("sha", payload.get("tree")),
                "parents", payload.get("parents"));

        commits.put(sha, document);
        return document.deepCopy();
    }

    private ObjectNode createRef(ObjectNode payload) {
        String ref = payload.path("ref").asText().substring("refs/".length());
        Preconditions.require(!refs.containsKey(ref), "Ref exists");
        String sha = payload.path("sha").asText();
        refs.put(ref, sha);
        return refDocument(sha);
    }

    private ObjectNode updateRef(String route, ObjectNode payload) {
        String ref = route.substring("/git/refs/".length());
        String sha = payload.path("sha").asText();
        Preconditions.require(!payload.path("force").asBoolean() && commits.get(sha).path("parents")
                .equals(JSONDocuments.value(List.of(refs.get(ref)))), "State compare-and-swap rejected");

        refs.put(ref, sha);
        return refDocument(sha);
    }

    private ObjectNode dispatch(ObjectNode payload) {
        if (rejectedDispatchStatus != null) {
            throw new GitHubRequestException(rejectedDispatchStatus, "POST", "/dispatches");
        }
        long runID = 700L + dispatches.size();
        dispatches.add(payload.deepCopy());
        var execution = execution();
        Preconditions.require(execution.phase() == ExecutionPhase.DISPATCHING, "Expected dispatching owner");
        runs.put(runID, run(execution, runID));
        if (dispatchFailure != null) {
            throw dispatchFailure;
        }
        return JSONDocuments.document("workflow_run_id", runID);
    }

    private ObjectNode dispatchIgnition(ObjectNode payload) {
        long runID = 800L + dispatches.size();
        dispatches.add(payload.deepCopy());
        runs.put(runID, JSONDocuments.document("id", runID, "run_attempt", 1,
                "status", "completed", "conclusion", conclusion));

        return JSONDocuments.document("workflow_run_id", runID, "html_url", "https://github.invalid/actions/runs/" + runID);
    }

    private ObjectNode workflowRuns(Map<String, String> query) {
        var items = runs.values().stream().filter(run -> run.path("head_sha").asText().equals(query.get("head_sha")))
                .map(ObjectNode::deepCopy).toList();
        int start = (Integer.parseInt(query.get("page")) - 1) * 100;
        return JSONDocuments.document("workflow_runs", JSONDocuments.arrayOf(items.subList(Math.min(start, items.size()),
                Math.min(start + 100, items.size()))));
    }

    private ObjectNode readRun(String route) {
        long runID = Long.parseLong(route.substring("/actions/runs/".length()));
        var pending = statuses.get(runID);
        if (pending != null && !pending.isEmpty()) {
            runs.get(runID).put("status", pending.removeFirst());
        }
        return runs.get(runID).deepCopy();
    }
}
