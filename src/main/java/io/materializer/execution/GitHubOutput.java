package io.materializer.execution;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.inject.Inject;

public final class GitHubOutput {

    @Inject
    public GitHubOutput() { }

    public void outputs(Path path, Map<String, String> values) {
        append(
                path,
                values.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue() + "\n").collect(Collectors.joining())
        );
    }

    public void append(Path path, String message) {
        try {
            Files.writeString(path, message, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
