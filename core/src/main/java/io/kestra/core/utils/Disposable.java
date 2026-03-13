package io.kestra.core.utils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@code Disposable} represents a resource or action that can be released or performed exactly once.
 * <p>
 * Typical use cases include closing resources, unregistering listeners, or performing cleanup logic
 * that should only run once, even if triggered multiple times or from multiple threads.
 * <p>
 */
public interface Disposable {

    /**
     * Disposes this object by running its associated cleanup action.
     * <p>
     * Implementations should guarantee that this method can be safely
     * called multiple times, but that the cleanup action is performed only once.
     * <p>
     * After the first successful call, subsequent invocations have no effect.
     */
    void dispose();

    /**
     * Returns whether this {@code Disposable} has already been disposed.
     *
     * @return {@code true} if the underlying action has already been executed,
     *         {@code false} otherwise
     */
    boolean isDisposed();

    /**
     * Creates a new {@code Disposable} from a list of disposable.
     *
     * @param disposables The list.
     * @return  a new {@code Disposable}
     */
    static Disposable of(List<Disposable> disposables) {
        return of(() -> disposables.forEach(Disposable::dispose));
    }

    /**
     * Creates a new {@code Disposable} from the given {@link Runnable} action.
     * <p>
     * The returned {@code Disposable} will execute the provided action exactly once,
     * even if {@link #dispose()} is called multiple times or concurrently
     * from multiple threads.
     *
     * @param action the cleanup action to execute when this {@code Disposable} is disposed
     * @return a new thread-safe {@code Disposable} wrapping the given action
     * @throws NullPointerException if {@code action} is {@code null}
     */
    static Disposable of(final Runnable action) {
        return new FromRunnable(action);
    }

    /**
     * Simple {@link Disposable} implementation that runs a given {@link Runnable} on {@link #dispose()} invocation.
     */
    class FromRunnable implements Disposable {
        private final AtomicBoolean disposed = new AtomicBoolean(false);
        private final Runnable action;

        FromRunnable(final Runnable action) {
            this.action = Objects.requireNonNull(action, "action must not be null");
        }

        @Override
        public void dispose() {
            if (disposed.compareAndSet(false, true)) {
                action.run();
            }
        }

        @Override
        public boolean isDisposed() {
            return disposed.get();
        }
    }
}