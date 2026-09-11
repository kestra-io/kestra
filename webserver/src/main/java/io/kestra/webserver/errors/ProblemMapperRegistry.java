package io.kestra.webserver.errors;

import java.util.List;
import java.util.Objects;
import java.util.Optional;


import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Resolves the {@link ProblemType} for a throwable by consulting every {@link ProblemMapper} in order,
 * defaulting to {@link ProblemTypes#INTERNAL_ERROR} when none matches.
 *
 * <p>Causes are deliberately not unwrapped: an exception is mapped on its own type only. Unwrapping would
 * let an internal failure inherit a client-error status from whatever it happens to wrap. Wrappers that do
 * carry a meaningful cause are handled explicitly by their own mapper or error handler.
 */
@Singleton
public class ProblemMapperRegistry {
    private final List<ProblemMapper> mappers;

    @Inject
    public ProblemMapperRegistry(final List<ProblemMapper> mappers) {
        this.mappers = Objects.requireNonNull(mappers, "mappers must not be null");
    }

    public ProblemType resolve(final Throwable throwable) {
        if (throwable == null) {
            return ProblemTypes.INTERNAL_ERROR;
        }
        for (ProblemMapper mapper : this.mappers) {
            Optional<ProblemType> resolved = mapper.map(throwable);
            if (resolved.isPresent()) {
                return resolved.get();
            }
        }
        return ProblemTypes.INTERNAL_ERROR;
    }
}
