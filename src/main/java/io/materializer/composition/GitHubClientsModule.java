package io.materializer.composition;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import io.materializer.github.GitHubAPI;
import io.materializer.github.GitHubClient;
import io.materializer.github.Polling;

public final class GitHubClientsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(GitHubClient.class).in(Singleton.class);
        bind(GitHubAPI.class).to(GitHubClient.class);
        bind(Polling.class).toInstance(Polling.system());
    }

    public static void closeResources(Injector injector) {
        injector.getInstance(GitHubClient.class).close();
    }
}
