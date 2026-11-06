package io.materializer.composition;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.materializer.environment.EnvironmentVariablesFactory;
import io.materializer.environment.models.EnvironmentVariables;

public class EnvironmentModule extends AbstractModule {

    @Provides
    @Singleton
    EnvironmentVariables provideEnvironmentVariables() {
        return EnvironmentVariablesFactory.from(System.getenv());
    }
}
