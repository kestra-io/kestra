package io.kestra.core.ai.usage.models;

import io.micronaut.core.annotation.Nullable;

import java.time.Instant;

/**
 * What one AI model call cost, as the provider reported it. Recorded for every provider, since the counts come
 * from the model response.
 *
 * <p>Per <em>model call</em> rather than per turn: an agent turn is a dozen or more calls whose cache hit rates
 * differ, and weighting cached input separately is only meaningful per call. Aggregating afterwards is free;
 * splitting a turn back into calls is not possible.
 *
 * <p>Counts are stored as reported rather than pre-weighted, so re-pricing leaves history comparable.
 *
 * @param uid                unique id of this record
 * @param tenant             the tenant that spent it, which keeps history attributable; no ceiling is scoped by it
 * @param providerId         the configured provider this call went to
 * @param userId             the caller, where there is a user identity to attribute it to
 * @param model              the model that answered, as the provider named it
 * @param recordedAt         when the call completed
 * @param promptTokens       total prompt tokens, inclusive of {@code cachedPromptTokens}
 * @param cachedPromptTokens the cached share of the prompt, billed at a fraction of the cold rate
 * @param completionTokens   generated output tokens, excluding thoughts
 * @param thoughtTokens      thinking tokens; billed at the output rate but reported separately, so kept separate
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
