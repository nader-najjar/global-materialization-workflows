package io.materializer.github;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.google.inject.Inject;
import io.materializer.environment.models.GitHubSettings;
import io.materializer.execution.InputSanitizer;
import io.materializer.github.exceptions.GitHubRequestException;
import io.materializer.github.models.GitHubRequest;
import io.materializer.github.models.GitHubResponse;
import io.materializer.shared.utilities.CanonicalJsonUtility;
import io.materializer.shared.utilities.Preconditions;

public final class GitHubClient implements GitHubAPI {

    private final String endpoint;
    private final String token;
    private final HttpClient client;
    private final InputSanitizer sanitizer;

    @Inject
    public GitHubClient(GitHubSettings settings, InputSanitizer sanitizer) {
        this.token = settings.token();
        this.endpoint = settings.url().replaceAll("/+$", "") + "/repos/" + settings.repository();
        this.sanitizer = sanitizer;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Override
    public <T extends GitHubResponse> T request(String method, String path, GitHubRequest payload, Class<T> responseType) {
        var body = payload == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(CanonicalJsonUtility.compact(sanitizer.validate(payload)), StandardCharsets.UTF_8);

        var request = HttpRequest.newBuilder(URI.create(endpoint + path)).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .header("X-GitHub-Api-Version", "2026-03-10").method(method, body).build();

        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Preconditions.require(response.statusCode() >= 200, "Invalid GitHub response status");
            if (response.statusCode() >= 300) {
                throw new GitHubRequestException(response.statusCode(), method, path);
            }
            return sanitizer.parseResponse(response.body(), responseType);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub request interrupted", exception);
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
