package io.materializer.ignition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.materializer.ignition.exceptions.InvalidMaterializerRegistryException;
import io.materializer.ignition.models.MaterializerRegistry;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterializerRegistryReaderTest {

    @TempDir
    private Path directory;

    private final MaterializerRegistryReader reader = new MaterializerRegistryReader();

    @Test
    void readsDistinctRegistrationsInOrder() throws IOException {
        MaterializerRegistry registry = reader.read(writeRegistry("""
                {"registrations":[{"repository":"owner/first"},{"repository":"owner/second"}]}
                """));

        assertEquals(
                List.of("owner/first", "owner/second"),
                registry.registrations().stream().map(registration -> registration.repository()).toList()
        );
    }

    @Test
    void permitsAnEmptyRegistry() throws IOException {
        assertTrue(reader.read(writeRegistry("{\"registrations\":[]}")).registrations().isEmpty());
    }

    @Test
    void reportsMissingFilesAsReadFailures() {
        Path missing = directory.resolve("missing.json");
        InvalidMaterializerRegistryException exception = assertThrows(
                InvalidMaterializerRegistryException.class,
                () -> reader.read(missing)
        );

        assertInstanceOf(IOException.class, exception.getCause());
        assertTrue(exception.getMessage().contains(missing.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{", "[]", "{}", "{\"registrations\":[]} {}", "{\"registrations\":[],\"registrations\":[]}"
    })
    void rejectsMalformedMissingDuplicateAndTrailingJSON(String json) throws IOException {
        Path file = writeRegistry(json);
        InvalidMaterializerRegistryException exception = assertThrows(
                InvalidMaterializerRegistryException.class,
                () -> reader.read(file)
        );

        assertInstanceOf(JsonProcessingException.class, exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "null",
        "{\"registrations\":null}",
        "{\"registrations\":[null]}",
        "{\"registrations\":[{\"repository\":null}]}",
        "{\"registrations\":[{\"repository\":\"invalid\"}]}"
    })
    void validatesTheRegistryAndItsNestedEntries(String json) throws IOException {
        Path file = writeRegistry(json);
        InvalidMaterializerRegistryException exception = assertThrows(
                InvalidMaterializerRegistryException.class,
                () -> reader.read(file)
        );

        assertInstanceOf(ValidationException.class, exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner/repo", "OWNER/REPO"})
    void rejectsDuplicateRepositoriesRegardlessOfCase(String repository) throws IOException {
        Path file = writeRegistry("""
                {"registrations":[{"repository":"owner/repo"},{"repository":"%s"}]}\
                """.formatted(repository));

        InvalidMaterializerRegistryException exception = assertThrows(
                InvalidMaterializerRegistryException.class,
                () -> reader.read(file)
        );

        assertEquals("Duplicate materializer repository: " + repository, exception.getMessage());
    }

    private Path writeRegistry(String json) throws IOException {
        return Files.writeString(directory.resolve("registry.json"), json);
    }
}
