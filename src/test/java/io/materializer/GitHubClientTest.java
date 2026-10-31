package io.materializer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.materializer.environment.models.GitHubSettings;
import io.materializer.execution.InputSanitizer;
import io.materializer.github.GitHubClient;
import io.materializer.github.exceptions.GitHubRequestException;
import io.materializer.github.models.MaterializerDispatchRequest;
import io.materializer.github.models.MaterializerDispatchResult;
import io.materializer.github.models.MaterializerInputs;
import io.materializer.json.JSONDocuments;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubClientTest {

    private HttpServer server;
    private GitHubClient client;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> version = new AtomicReference<>();
    private final AtomicReference<String> payload = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/example/materializer", this::respond);
        server.start();
        client = new GitHubClient(
                new GitHubSettings("http://127.0.0.1:" + server.getAddress().getPort(), "example/materializer", "offline-token"),
                new InputSanitizer()
        );
    }

    @AfterEach
    void close() {
        client.close();
        server.stop(0);
    }

    private void respond(HttpExchange exchange) throws IOException {
        method.set(exchange.getRequestMethod());
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        version.set(exchange.getRequestHeaders().getFirst("X-GitHub-Api-Version"));
        payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String path = exchange.getRequestURI().getPath();
        int status = path.endsWith("/missing") ? 404 : 200;
        String body = path.endsWith("/duplicate") ? "{\"id\":1,\"id\":2}" : "{\"workflow_run_id\":700}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (exchange) {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    @Test
    void dispatchRetainsAcknowledgementPayloadAndGitHubHeaders() {
        var sent = new MaterializerDispatchRequest("pipeline-executions/100-1", true, new MaterializerInputs("a".repeat(40)));

        var response = client.request("POST", "/actions/workflows/materializer.yml/dispatches", sent, MaterializerDispatchResult.class);
        assertEquals(700, response.runID());
        assertEquals("POST", method.get());
        assertEquals("Bearer offline-token", authorization.get());
        assertEquals("2026-03-10", version.get());
        assertEquals(JSONDocuments.value(sent), JSONDocuments.parse(payload.get()));
    }

    @Test
    void optionalMissingResourceIsDistinctFromARequiredRead() {
        assertNull(client.getOptional("/missing", MaterializerDispatchResult.class));
        var failure = assertThrows(GitHubRequestException.class, () -> client.get("/missing", MaterializerDispatchResult.class));
        assertEquals(404, failure.status());
        assertEquals("GitHub request failed: GET /missing returned 404", failure.getMessage());
    }

    @Test
    void ambiguousJSONResponseIsRejectedBeforeAcknowledgement() {
        var failure = assertThrows(IllegalArgumentException.class, () -> client.get("/duplicate", MaterializerDispatchResult.class));
        assertTrue(failure.getMessage().contains("Duplicate"));
    }
}
