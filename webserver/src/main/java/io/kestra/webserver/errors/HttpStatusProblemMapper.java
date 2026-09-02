package io.kestra.webserver.errors;

import java.util.Optional;


import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;

/**
 * Maps exceptions that already carry the status they want, so their type follows from that status rather
 * than from their class. Ordered ahead of the class-based tables because those two exception types are far
 * too broad to place in one.
 */
@Singleton
public class HttpStatusProblemMapper implements ProblemMapper {
    @Override
    public int getOrder() {
        return -1000;
    }

    @Override
    public Optional<ProblemType> map(final Throwable throwable) {
        if (throwable instanceof HttpStatusException e) {
            return Optional.of(ProblemTypes.byStatus(e.getStatus().getCode()));
        }
        // The status of an upstream call, passed through. Reporting it as our own is misleading, but changing
        // that would turn client errors into server errors; tracked as a follow-up.
        if (throwable instanceof HttpClientResponseException e) {
            return Optional.of(ProblemTypes.byStatus(statusOf(e)));
        }
        return Optional.empty();
    }

    private static int statusOf(final HttpClientResponseException e) {
        HttpStatus status = e.getStatus();
        return status == null ? HttpStatus.INTERNAL_SERVER_ERROR.getCode() : status.getCode();
    }
}
