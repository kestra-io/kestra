package io.kestra.core.runners;

import java.util.Set;

import io.kestra.core.storages.InternalNamespace;
import io.kestra.core.storages.InternalStorage;
import io.kestra.core.utils.Rethrow;

/**
 * Guardrail marker used to flag that the current dynamic extent of execution is performing
 * framework-internal file I/O — as opposed to raw I/O issued directly by plugin code — using a
 * {@link ScopedValue}.
 * <p>
 * This is a defense-in-depth guardrail, not a security boundary. {@link #call} and {@link #run} only
 * grant the privilege when their immediate caller is one of {@link #TRUSTED_CALLERS}, checked via
 * {@link StackWalker} class-identity — not string matching, and only the immediate caller, so a trusted
 * frame lingering elsewhere on the stack grants nothing. This closes the trivial bypass of a plugin
 * calling {@code PrivilegedIo.run(...)} directly on its own I/O. It does not close every bypass: a
 * sufficiently privileged caller can still reflectively grab the private {@link ScopedValue} field and
 * rebind it directly, skipping this class entirely. That residual risk is accepted; this is a guardrail
 * against fragile heuristics, not an adversarial security boundary.
 * <p>
 * Callers must wrap this as narrowly as possible around the actual file I/O being performed, and never
 * around code that may call back into plugin code.
 */
public final class PrivilegedIo {
    private static final ScopedValue<Boolean> INTERNAL_IO = ScopedValue.newInstance();
    private static final Set<Class<?>> TRUSTED_CALLERS = Set.of(DefaultRunContext.class, InternalStorage.class, InternalNamespace.class);
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private PrivilegedIo() {
        // utility class pattern
    }

    /**
     * Runs the given supplier inside a privilege section, and returns its result.
     *
     * @throws SecurityException if the immediate caller is not a trusted framework class.
     */
    public static <T, E extends Exception> T call(Rethrow.SupplierChecked<T, E> supplier) throws E {
        requireTrustedCaller();
        try {
            return ScopedValue.where(INTERNAL_IO, Boolean.TRUE).call(() -> Rethrow.throwSupplier(supplier).get());
        } catch (Exception e) {
            return sneakyThrow(e);
        }
    }

    /**
     * Runs the given runnable with the internal-I/O marker bound.
     *
     * @throws SecurityException if the immediate caller is not a trusted framework class.
     */
    public static <E extends Exception> void run(Rethrow.RunnableChecked<E> runnable) throws E {
        requireTrustedCaller();
        ScopedValue.where(INTERNAL_IO, Boolean.TRUE).run(Rethrow.throwRunnable(runnable));
    }

    /**
     * @return {@code true} if the current thread is inside a privileged section.
     */
    public static boolean isActive() {
        return INTERNAL_IO.orElse(Boolean.FALSE);
    }

    private static void requireTrustedCaller() {
        Class<?> caller = STACK_WALKER.walk(
            frames -> frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .filter(declaringClass -> declaringClass != PrivilegedIo.class)
                .findFirst()
        ).orElse(null);

        if (!TRUSTED_CALLERS.contains(caller)) {
            throw new SecurityException("PrivilegedIo may only be invoked by trusted framework classes, got: " + (caller == null ? "unknown" : caller.getName()));
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception, T> T sneakyThrow(Exception exception) throws E {
        throw (E) exception;
    }
}
