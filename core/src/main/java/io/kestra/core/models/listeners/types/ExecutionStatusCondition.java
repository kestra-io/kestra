package io.kestra.core.models.listeners.types;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Keep to avoid BC
 */
@Deprecated
@Schema(
    title = "Deprecated, use `io.kestra.core.models.conditions.types.ExecutionStatusCondition`"
)
public class ExecutionStatusCondition extends io.kestra.core.models.conditions.types.ExecutionStatusCondition {

}
