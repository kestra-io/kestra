package io.kestra.webserver.errors;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Base class for the common case: a static exception-class to {@link ProblemType} table.
 *
 * <p>Lookup walks up the exception's superclass chain, so registering a base type covers its subclasses
 * unless one of them is registered more specifically.
 */
public abstract class ExceptionTypeProblemMapper implements ProblemMapper {
    private final Map<Class<? extends Throwable>, ProblemType> table = new LinkedHashMap<>();

    protected ExceptionTypeProblemMapper() {
        this.register(this.table::put);
    }

    /** Declares this mapper's table by calling {@code to.accept(exceptionClass, problemType)} for each entry. */
    protected abstract void register(BiConsumer<Class<? extends Throwable>, ProblemType> to);

    @Override
    public Optional<ProblemType> map(final Throwable throwable) {
        for (Class<?> candidate = throwable.getClass(); Throwable.class.isAssignableFrom(candidate); candidate = candidate.getSuperclass()) {
            ProblemType type = this.table.get(candidate);
            if (type != null) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
