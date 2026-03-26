package io.kestra.core.models.flows;

import java.util.List;

import jakarta.annotation.Nullable;

/**
 * Defines a dependency between inputs.
 *
 * @see io.kestra.core.models.flows.Input
 *
 * @param inputs The inputs
 * @param condition The conditional expression.
 */
public record DependsOn(
    @Nullable List<String> inputs,
    @Nullable String condition) {
}
