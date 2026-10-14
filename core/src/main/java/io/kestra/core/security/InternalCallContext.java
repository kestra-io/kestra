package io.kestra.core.security;

import java.util.function.Supplier;

/**
 * Marks the calling thread as serving an internal call: one issued by another Kestra server rather
 * than by a user, such as a worker asking the controller for namespace file metadata over gRPC.
 * <p>
 * Row-level permission filters are keyed on the authenticated user, and an internal caller has
 * none, so enforcing them filters every row out instead of restricting anything. The marker is
 * carried per call rather than derived from the server type because a webserver serves user
 * requests and internal worker calls from the same process.
 * <p>
 * The marker is unbound by default and bound only for the duration of the given action, so a caller
 * that does not set it is treated as a user request and stays subject to permission filtering.
 */
public final class InternalCallContext {

    private static final ScopedValue<Boolean> INTERNAL_CALL = ScopedValue.newInstance();

    private InternalCallContext() {
    }

    /**
     * @return {@code true} if the calling thread is currently serving an internal call.
     */
    public static boolean isInternalCall() {
        return INTERNAL_CALL.isBound();
    }

    /**
     * Runs the given action with the calling thread marked as serving an internal call.
     */
    public static void runAsInternalCall(final Runnable action) {
        ScopedValue.where(INTERNAL_CALL, Boolean.TRUE).run(action);
    }

    /**
     * Same as {@link #runAsInternalCall(Runnable)} for an action that returns a value.
     */
    public static <T> T callAsInternalCall(final Supplier<T> action) {
        return ScopedValue.where(INTERNAL_CALL, Boolean.TRUE).call(action::get);
    }
}
