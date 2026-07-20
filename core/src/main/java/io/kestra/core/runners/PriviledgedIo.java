package io.kestra.core.runners;

import io.kestra.core.utils.Rethrow;

/**
 * Guardrail marker used to flag that the current dynamic extent of execution is performing
 * framework-internal file I/O — as opposed to raw I/O issued directly by plugin code — using a
 * {@link ScopedValue}.
 * <p>
 * This is a defense-in-depth guardrail, not a security boundary: it is enforced entirely in-process
 * and, like any in-JVM marker, can be defeated by a malicious plugin using reflection to rebind the
 * underlying {@link ScopedValue} key. It exists solely so the EE worker sandbox can distinguish "the
 * framework doing its own I/O on the caller's behalf" from "a plugin doing raw I/O", without relying on
 * stack-trace class-name matching, which is fragile because a trusted frame may still be on the stack
 * while a plugin performs its own, unrelated I/O.
 * <p>
 * Callers must wrap this as narrowly as possible around the actual file I/O being performed, and never
 * around code that may call back into plugin code.
 */
public final class PriviledgedIo {
    private static final ScopedValue<Boolean> INTERNAL_IO = ScopedValue.newInstance();

    private PriviledgedIo() {
        // utility class pattern
    }

    /**
     * Runs the given supplier with the internal-I/O marker bound, and returns its result.
     */
    public static <T, E extends Exception> T call(Rethrow.SupplierChecked<T, E> supplier) throws E {
        try {
            return ScopedValue.where(INTERNAL_IO, Boolean.TRUE).call(() -> Rethrow.throwSupplier(supplier).get());
        } catch (Exception e) {
            return sneakyThrow(e);
        }
    }

    /**
     * Runs the given runnable with the internal-I/O marker bound.
     */
    public static <E extends Exception> void run(Rethrow.RunnableChecked<E> runnable) throws E {
        ScopedValue.where(INTERNAL_IO, Boolean.TRUE).run(Rethrow.throwRunnable(runnable));
    }

    /**
     * @return {@code true} if the current thread is dynamically inside a call to {@link #call} or {@link #run}.
     */
    public static boolean isActive() {
        return INTERNAL_IO.orElse(Boolean.FALSE);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception, T> T sneakyThrow(Exception exception) throws E {
        throw (E) exception;
    }
}
