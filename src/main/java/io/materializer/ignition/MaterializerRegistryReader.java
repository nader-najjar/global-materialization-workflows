package io.materializer.ignition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import io.materializer.ignition.exceptions.InvalidMaterializerRegistryException;
import io.materializer.ignition.models.MaterializerRegistration;
import io.materializer.ignition.models.MaterializerRegistry;
import io.materializer.shared.utilities.HibernateValidatorUtility;
import io.materializer.shared.utilities.JsonMapperUtility;
import jakarta.validation.ValidationException;

public class MaterializerRegistryReader {

    @Inject
    public MaterializerRegistryReader() { }

    public MaterializerRegistry read(Path path) {
        MaterializerRegistry registry;
        try {
            registry = JsonMapperUtility.MAPPER.readValue(Files.readString(path), MaterializerRegistry.class);
            HibernateValidatorUtility.validate(registry);

        } catch (JsonProcessingException exception) {
            throw new InvalidMaterializerRegistryException("Invalid materializer registry JSON: " + path, exception);

        } catch (IOException exception) {
            throw new InvalidMaterializerRegistryException("Could not read materializer registry: " + path, exception);

        } catch (ValidationException exception) {
            throw new InvalidMaterializerRegistryException("Invalid materializer registry: " + path, exception);
        }

        validateUniqueRepositories(registry.registrations());
        return registry;
    }

    private void validateUniqueRepositories(List<MaterializerRegistration> registrations) {
        Set<String> repositories = new HashSet<>();
        for (MaterializerRegistration registration : registrations) {
            validateUniqueRepository(registration.repository(), repositories);
            repositories.add(registration.repository().toLowerCase(Locale.ROOT));
        }
    }

    private void validateUniqueRepository(String repository, Set<String> repositories) {
        if (repositories.contains(repository.toLowerCase(Locale.ROOT))) {
            throw new InvalidMaterializerRegistryException("Duplicate materializer repository: " + repository);
        }
    }
}
