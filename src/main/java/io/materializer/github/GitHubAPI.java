package io.materializer.github;

import io.materializer.github.exceptions.GitHubRequestException;
import io.materializer.github.models.GitHubRequest;
import io.materializer.github.models.GitHubResponse;

public interface GitHubAPI extends AutoCloseable {

    <T extends GitHubResponse> T request(String method, String path, GitHubRequest payload, Class<T> responseType);

    default <T extends GitHubResponse> T get(String path, Class<T> responseType) {
        return request("GET", path, null, responseType);
    }

    default <T extends GitHubResponse> T getOptional(String path, Class<T> responseType) {
        try {
            return get(path, responseType);
        } catch (GitHubRequestException exception) {
            if (exception.status() == 404) {
                return null;
            }
            throw exception;
        }
    }

    @Override
    default void close() { }
}
