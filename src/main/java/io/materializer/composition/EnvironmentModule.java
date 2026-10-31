package io.materializer.composition;

import java.util.function.Supplier;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.materializer.environment.models.EnvironmentVariables;
import io.materializer.environment.models.GitHubSettings;

public final class EnvironmentModule<T extends EnvironmentVariables> extends AbstractModule {

    private final Class<T> type;
    private final Supplier<T> source;

    public EnvironmentModule(Class<T> type, Supplier<T> source) {
        this.type = type;
        this.source = source;
    }

    @Override
    protected void configure() {
        bind(type).toProvider(source::get).in(Singleton.class);
        bind(EnvironmentVariables.class).to(type);
    }

    @Provides
    @Singleton
    GitHubSettings provideGitHubSettings(EnvironmentVariables environment) {
        return environment.github();
    }
}
