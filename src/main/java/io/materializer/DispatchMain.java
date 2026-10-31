package io.materializer;

import io.materializer.composition.EnvironmentModule;
import io.materializer.environment.EnvironmentVariablesFactory;
import io.materializer.environment.models.DispatchEnvironment;
import io.materializer.execution.DispatchExecutor;

public final class DispatchMain {

    private DispatchMain() { }

    public static void main(String[] args) {
        int exitCode = MainEntrypoint.run(
                new EnvironmentModule<>(DispatchEnvironment.class, () -> EnvironmentVariablesFactory.dispatch(System.getenv())),
                DispatchExecutor.class
        );

        System.exit(exitCode);
    }
}
