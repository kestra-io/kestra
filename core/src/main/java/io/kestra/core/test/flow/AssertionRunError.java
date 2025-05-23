package io.kestra.core.test.flow;

import jakarta.validation.constraints.NotNull;

public record AssertionRunError(@NotNull String message, String details) {
}
