package io.kestra.webserver.controllers.api;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.webserver.services.ai.agent.data.ApiThreadSummary;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the {@code maxTurnsPerThread} cost/abuse guardrail: once a thread has held its maximum number
 * of user turns, a new chat turn is refused. Cap is set to 1 here so the second turn is refused.
 */
@KestraTest
@Property(name = "kestra.ai.agent.max-turns-per-thread", value = "1")
class AiAgentControllerTurnLimitTest extends AbstractAiAgentControllerTest {

    @Test
    void shouldRefuseNewTurnWhenThreadTurnCapReached() {
        // Given — a thread that has already run one full turn (the cap)
        ApiThreadSummary thread = createThread();
        chat(thread.uid(), "first?", "first answer");

        // When — a second turn is attempted on the same thread (no response scripted: it never reaches the model)
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(chatRequest(thread.uid(), "second?"), String.class)
        );

        // Then — refused with 429 TOO_MANY_REQUESTS
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.getCode());
    }
}
