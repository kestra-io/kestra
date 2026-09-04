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
 * Verifies the {@code maxTurnContextChars} guardrail: once the conversation a turn would send to the model
 * has grown past the cap, further chat turns are refused rather than sent at an ever-growing cost. The cap
 * is set low here so a single ordinary exchange exceeds it.
 */
@KestraTest
@Property(name = "kestra.ai.agent.max-turn-context-chars", value = "40")
class AiAgentControllerContextLimitTest extends AbstractAiAgentControllerTest {

    @Test
    void shouldRefuseNewTurnWhenConversationContextTooLarge() {
        // Given — a short first turn whose answer grows the thread past the 40-character context cap
        ApiThreadSummary thread = createThread();
        chat(thread.uid(), "hi", "a".repeat(60));

        // When — a further turn is attempted on the now-oversized thread
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(chatRequest(thread.uid(), "more?"), String.class)
        );

        // Then — refused with 413, distinguishable from the 429 the turn-count cap returns
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.REQUEST_ENTITY_TOO_LARGE.getCode());
        assertThat(e.getMessage()).contains("too large to continue");
    }

    @Test
    void shouldAllowFirstTurnWhenPromptWithinContextBudget() {
        // Given — an empty thread and a prompt below the cap
        ApiThreadSummary thread = createThread();

        // When / Then — the turn runs; the guard only refuses once the conversation has grown
        assertThat(chat(thread.uid(), "short?", "ok")).isNotEmpty();
    }

    @Test
    void shouldRefuseTurnWhenPromptAloneExceedsContextBudget() {
        // Given — an empty thread, so only the incoming prompt counts toward the budget
        ApiThreadSummary thread = createThread();

        // When
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(chatRequest(thread.uid(), "x".repeat(41)), String.class)
        );

        // Then — the prompt itself is part of the context the turn would send
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.REQUEST_ENTITY_TOO_LARGE.getCode());
    }
}
