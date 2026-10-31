package io.materializer;

import io.materializer.composition.EnvironmentModule;
import io.materializer.environment.EnvironmentVariablesFactory;
import io.materializer.environment.models.MatrixEnvironment;
import io.materializer.execution.BuildMatrixExecutor;

public final class BuildMatrixMain {

    private BuildMatrixMain() { }

    public static void main(String[] args) {
        int exitCode = MainEntrypoint.run(
                new EnvironmentModule<>(MatrixEnvironment.class, () -> EnvironmentVariablesFactory.matrix(System.getenv())),
                BuildMatrixExecutor.class
        );

        System.exit(exitCode);
    }
}
