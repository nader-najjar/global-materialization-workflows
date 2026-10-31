package io.materializer;

import io.materializer.composition.EnvironmentModule;
import io.materializer.environment.EnvironmentVariablesFactory;
import io.materializer.environment.models.IgnitionEnvironment;
import io.materializer.execution.IgnitionExecutor;

public final class IgnitionMain {

    private IgnitionMain() { }

    public static void main(String[] args) {
        int exitCode = MainEntrypoint.run(
                new EnvironmentModule<>(IgnitionEnvironment.class, () -> EnvironmentVariablesFactory.ignition(System.getenv())),
                IgnitionExecutor.class
        );

        System.exit(exitCode);
    }
}
