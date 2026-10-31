package io.materializer.environment.models;

import java.nio.file.Path;

public sealed interface EnvironmentVariables permits IgnitionEnvironment, DispatchEnvironment, MatrixEnvironment {

    GitHubSettings github();

    Path output();
}
