package io.kestra.core.test.flow;

import jakarta.validation.constraints.NotNull;

public record AssertionResult(
    @NotNull
    String operator,
    @NotNull
    Object expected,
    @NotNull
    Object actual,
    @NotNull
    Boolean isSuccess,

    String taskId,

    String description,
    String errorMessage
) {
}
