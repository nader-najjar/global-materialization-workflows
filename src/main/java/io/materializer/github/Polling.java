package io.materializer.github;

import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class Polling {

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final LongSupplier clock;
    private final Sleeper sleeper;

    public Polling(LongSupplier clock, Sleeper sleeper) {
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public static Polling system() {
        return new Polling(System::nanoTime, Thread::sleep);
    }

    public <T> T await(Supplier<T> read, Duration timeout, Duration interval, String message) {
        long deadline = clock.getAsLong() + timeout.toNanos();
        T result = read.get();
        while (result == null) {
            waitInterval(deadline, interval, message);
            result = read.get();
        }
        return result;
    }

    private void waitInterval(long deadline, Duration interval, String message) {
        checkDeadline(deadline, message);
        try {
            sleeper.sleep(interval);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Polling interrupted", exception);
        }
        checkDeadline(deadline, message);
    }

    private void checkDeadline(long deadline, String message) {
        if (clock.getAsLong() >= deadline) {
            throw new IllegalStateException(message);
        }
    }
}
