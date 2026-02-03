package io.kestra.core.tests.flow;

import jakarta.validation.constraints.NotNull;

public record AssertionRunError(@NotNull String message, String details) {
}
