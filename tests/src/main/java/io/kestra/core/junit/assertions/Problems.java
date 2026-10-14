package io.kestra.core.junit.assertions;

import java.util.List;

import io.kestra.webserver.errors.ProblemDetail;
import io.kestra.webserver.errors.ProblemError;
import io.kestra.webserver.errors.ProblemType;

import io.micronaut.http.MediaType;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.assertj.core.api.ListAssert;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assertions on RFC 9457 problem responses.
 *
 * <p>Prefer these over reading {@code HttpClientResponseException#getMessage()}: Micronaut's error decoder
 * parses a body only for exactly {@code application/json}, so for {@code application/problem+json}
 * {@code getMessage()} returns the whole raw JSON document.
 */
public final class Problems {
    private Problems() {
    }

    /**
     * Asserts the exception carries a problem document of the expected type, with the status that type
     * declares, and returns the document for further assertions.
     */
    public static ProblemDetail assertProblem(final HttpClientResponseException e, final ProblemType expected) {
        ProblemDetail problem = of(e);

        assertThat(problem.type())
            .withFailMessage("Expected problem type '%s' but got '%s' (detail: %s)", expected.slug(), problem.type(), problem.detail())
            .isEqualTo(expected.uri());
        assertThat(problem.title()).isEqualTo(expected.title());
        assertThat(problem.status()).isEqualTo(expected.status());
        assertThat(e.getStatus().getCode()).isEqualTo(expected.status());

        if (expected.isServerError()) {
            assertThat(problem.traceId()).withFailMessage("A server error must carry a traceId").isNotBlank();
        } else {
            assertThat(problem.traceId()).withFailMessage("A client error must not carry a traceId").isNull();
        }
        return problem;
    }

    /** The problem document, failing the test if the response is not one. */
    public static ProblemDetail of(final HttpClientResponseException e) {
        assertThat(e.getResponse().getContentType())
            .withFailMessage("Every error response must be served as %s", MediaType.APPLICATION_JSON_PROBLEM)
            .hasValue(MediaType.APPLICATION_JSON_PROBLEM_TYPE);

        return e.getResponse()
            .getBody(ProblemDetail.class)
            .orElseThrow(() -> new AssertionError("Expected a problem document but the response had no body"));
    }

    /** The {@code detail} member, replacing the previous {@code getMessage()} idiom. */
    public static String detail(final HttpClientResponseException e) {
        return of(e).detail();
    }

    public static ListAssert<ProblemError> assertErrors(final HttpClientResponseException e) {
        List<ProblemError> errors = of(e).errors();
        return assertThat(errors == null ? List.<ProblemError>of() : errors);
    }
}
