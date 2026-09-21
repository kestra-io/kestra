package io.kestra.webserver.errors;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemTypesTest {
    @Test
    void shouldDeclareUniqueSlugs() {
        // Given every declared type
        // Then no two share a slug: the slug is the published identity, so a duplicate would give two
        // different kinds of failure the same documented URI.
        assertThat(declaredTypes().map(ProblemType::slug).toList()).doesNotHaveDuplicates();
    }

    @Test
    void shouldMapStatusToATypeDeclaringThatSameStatus() {
        // Given a status byStatus recognises
        // Then the type it returns declares that status, so the document never contradicts the response
        for (int status : List.of(400, 401, 403, 404, 405, 406, 409, 410, 415, 422, 423, 429, 503, 504)) {
            assertThat(ProblemTypes.byStatus(status).status())
                .withFailMessage("byStatus(%d) must declare status %d", status, status)
                .isEqualTo(status);
        }
    }

    @Test
    void shouldNeverReportAnUnknownClientErrorAsAServerError() {
        // Given a status with no dedicated type
        // Then it collapses within its own class, so a client error is never reported as ours — which would
        // make clients retry something that will never succeed
        assertThat(ProblemTypes.byStatus(418).isServerError()).isFalse();
        assertThat(ProblemTypes.byStatus(451).isServerError()).isFalse();
        assertThat(ProblemTypes.byStatus(502).isServerError()).isTrue();
    }

    private static java.util.stream.Stream<ProblemType> declaredTypes() {
        return Arrays.stream(ProblemTypes.class.getDeclaredFields())
            .filter(field -> ProblemType.class.equals(field.getType()))
            .filter(field -> Modifier.isStatic(field.getModifiers()))
            .map(ProblemTypesTest::read);
    }

    private static ProblemType read(final Field field) {
        try {
            return (ProblemType) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(field.getName(), e);
        }
    }
}
