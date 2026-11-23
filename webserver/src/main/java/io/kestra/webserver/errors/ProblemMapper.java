package io.kestra.webserver.errors;

import java.util.Optional;

import io.micronaut.core.order.Ordered;

/**
 * Resolves a throwable to the {@link ProblemType} that should be reported for it.
 *
 * <p>Implementations are beans, consulted in {@link Ordered} order until one returns a result, which is
 * how Enterprise contributes mappings for its own exceptions without editing the Open Source table. Use
 * {@link ExceptionTypeProblemMapper} for the common case of a static exception-class to type table; implement
 * this interface directly only when the type depends on the exception instance rather than its class.
 *
 * @see ProblemMapperRegistry
 */
public interface ProblemMapper extends Ordered {
    Optional<ProblemType> map(Throwable throwable);
}
