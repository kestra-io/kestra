package io.kestra.core.ai.usage.models;

import io.micronaut.core.annotation.Nullable;

import java.time.Instant;

/**
 * What one AI model call cost, as the provider reported it.
 *
 * <p>Recorded for every provider, not only Kestra's hosted one: the counts come from the model response, which
 * every provider returns, so an operator can be shown — and later limited on — their own OpenAI or Ollama
 * spend by the same machinery.
 *
 * <p>Recorded per <em>model call</em> rather than per turn. An agent turn is a dozen or more calls whose cache
 * hit rates differ wildly, and weighting cached input at a tenth of cold input is only meaningful per call;
 * aggregating afterwards is free, while splitting a turn back into calls is not possible.
 *
 * <p>Counts are stored as reported rather than as a weighted total, so a change to the weights or the rate card
 * leaves yesterday's history comparable with today's.
 *
 * @param providerId       the configured provider this call went to, so limits and display are per provider
 * @param userId           the caller, when the edition has users;
 * @param promptTokens     total prompt tokens, inclusive of {@code cachedPromptTokens}
 * @param cachedPromptTokens the cached share of the prompt, billed at a fraction of the cold rate
 * @param completionTokens generated output tokens, excluding thoughts
 * @param thoughtTokens    thinking tokens; billed at the output rate but reported separately by providers that
 *                         report them at all, so kept separate rather than folded into output
 */
public record AiUsage(
    String uid,
    String tenant,
    String providerId,
    @Nullable String userId,
    @Nullable String model,
    Instant recordedAt,
    long promptTokens,
    long cachedPromptTokens,
    long completionTokens,
    long thoughtTokens
) {
}
