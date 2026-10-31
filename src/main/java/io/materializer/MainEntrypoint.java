package io.materializer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.materializer.composition.GitHubClientsModule;
import io.materializer.composition.StrictGuiceModule;
import io.materializer.execution.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MainEntrypoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainEntrypoint.class);
    private static final int SHUTDOWN_GRACE_PERIOD_SECONDS = 25;

    private final CountDownLatch mainFinished = new CountDownLatch(1);
    private volatile boolean shutdownRequested;

    private MainEntrypoint() { }

    public static int run(Module environment, Class<? extends Executor> executorType) {
        return new MainEntrypoint().execute(environment, executorType);
    }

    private int execute(Module environment, Class<? extends Executor> executorType) {
        Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stopMain(mainThread), "shutdown-hook"));

        int exitCode = 0;
        Injector injector = null;
        try {
            injector = Guice.createInjector(
                    com.google.inject.Stage.PRODUCTION,
                    new StrictGuiceModule(),
                    environment,
                    new GitHubClientsModule()
            );

            Executor executor = injector.getInstance(executorType);
            executor.execute();
        } catch (Exception exception) {
            logFailure(exception);
            exitCode = 1;
        } finally {
            closeResources(injector);
            mainFinished.countDown();
        }

        return exitCode;
    }

    private void logFailure(Exception exception) {
        if (!shutdownRequested) {
            LOGGER.error("Technical exception occurred at software entrypoint level: ", exception);
        } else {
            LOGGER.info("Stopped by shutdown request: {}", exception.toString());
        }
    }

    private static void closeResources(Injector injector) {
        if (Thread.interrupted()) {
            LOGGER.info("Shutdown interrupt cleared so resources can close");
        }
        if (injector == null) {
            return;
        }
        try {
            GitHubClientsModule.closeResources(injector);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to close resources", exception);
        }
    }

    private void stopMain(Thread mainThread) {
        shutdownRequested = true;
        mainThread.interrupt();
        try {
            if (!mainFinished.await(SHUTDOWN_GRACE_PERIOD_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.warn("Main did not finish within {}s; JVM will exit", SHUTDOWN_GRACE_PERIOD_SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Shutdown hook interrupted");
        }
    }
}
