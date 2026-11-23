package io.kestra.core.services;

import io.kestra.core.models.flows.Flow;

import io.micronaut.core.annotation.Nullable;

/**
 * What an {@link ExpressionContext} is built for.
 *
 * @param flow the flow the expressions are offered in, or {@code null} for the expressions available anywhere.
 * @param taskId the task the expressions are offered in, which limits task outputs to the tasks that run before it.
 */
public record ExpressionRequest(@Nullable Flow flow, @Nullable String taskId) {
    public static ExpressionRequest global() {
        return new ExpressionRequest(null, null);
    }
}
